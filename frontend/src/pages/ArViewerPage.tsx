import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import '@google/model-viewer'
import type { ModelViewerElement } from '@google/model-viewer'
import { getStoredUser } from '../api/client'
import { useApi } from '../api/useApi'
import type { ContentDetail, PartDto, Role } from '../api/types'
import ErrorBanner from '../components/ErrorBanner'
import MarkerScanner from '../components/MarkerScanner'
import Spinner from '../components/Spinner'

/**
 * AR lesson viewer — route `/ar/:contentId` (alias `/viewer/:contentId`).
 *
 * - 3D tab: `<model-viewer>` loads the content's `modelUrl` with AR enabled
 *   (webxr / scene-viewer / quick-look are chosen by model-viewer itself),
 *   one hotspot per content part, and a side panel with the part explanation.
 * - Scan tab: rear-camera QR scanner (jsQR) that resolves a printed marker to
 *   a lesson through `GET /api/ar-content/marker/{markerId}`.
 */

type Tab = 'viewer' | 'scan'

interface Vec3 {
  x: number
  y: number
  z: number
}

interface HotspotAnchor {
  position: string
  normal: string
}

const DEFAULT_CENTER: Vec3 = { x: 0, y: 0, z: 0 }
const DEFAULT_SIZE: Vec3 = { x: 1, y: 1, z: 1 }

/** Library route for the signed-in role (where the "Open AR" buttons live). */
function libraryPath(role: Role | undefined): string {
  switch (role) {
    case 'STUDENT':
      return '/student/lessons'
    case 'TEACHER':
      return '/teacher/content'
    case 'DEVELOPER':
      return '/developer/content'
    default:
      return '/'
  }
}

/**
 * Hotspot anchor for a part when the backend provides an explicit position
 * (`position` string or positionX/Y/Z numbers); `null` = auto-place.
 */
function explicitPosition(part: PartDto): string | null {
  const raw = part.position?.trim()
  if (raw) return raw
  const { positionX, positionY, positionZ } = part
  if (typeof positionX === 'number' && typeof positionY === 'number' && typeof positionZ === 'number') {
    return `${positionX} ${positionY} ${positionZ}`
  }
  return null
}

/** Unit normal pointing away from the model centre so labels face the camera side. */
function outwardNormal(position: string, center: Vec3): string {
  const nums = position.trim().split(/\s+/).map((value) => Number.parseFloat(value))
  if (nums.length < 3 || nums.some((n) => !Number.isFinite(n))) return '0 1 0'
  const dx = nums[0] - center.x
  const dz = nums[2] - center.z
  const len = Math.hypot(dx, dz)
  if (len < 1e-6) return '0 1 0'
  return `${dx / len} 0 ${dz / len}`
}

/**
 * Auto-place `count` labels evenly around the model's bounding cylinder, with a
 * gentle vertical spread so labels do not overlap when seen from the front.
 */
function autoAnchors(count: number, center: Vec3, size: Vec3): HotspotAnchor[] {
  const radius = Math.max(size.x, size.z) / 2 + Math.max(size.x, size.y, size.z) * 0.12
  const yMin = center.y - size.y / 2
  const yMax = center.y + size.y / 2
  return Array.from({ length: count }, (_, i) => {
    const angle = (i / count) * Math.PI * 2 - Math.PI / 2
    const nx = Math.cos(angle)
    const nz = Math.sin(angle)
    const spread = count === 1 ? 0.5 : i / (count - 1)
    const y = Math.min(yMax, Math.max(yMin, center.y + (0.5 - spread) * size.y * 0.7))
    return {
      position: `${center.x + radius * nx} ${y} ${center.z + radius * nz}`,
      normal: `${nx} 0 ${nz}`,
    }
  })
}

export default function ArViewerPage() {
  const { contentId } = useParams<{ contentId: string }>()
  const navigate = useNavigate()
  const user = getStoredUser()

  const detail = useApi<ContentDetail>(contentId ? `/api/ar-content/${contentId}` : null)
  const parts = useMemo(() => detail.data?.parts ?? [], [detail.data])

  const [tab, setTab] = useState<Tab>('viewer')
  const [selected, setSelected] = useState<PartDto | null>(null)
  const [anchors, setAnchors] = useState<Record<string, HotspotAnchor>>({})
  const [modelError, setModelError] = useState<string | null>(null)
  const [arNote, setArNote] = useState<string | null>(null)
  const [loaded, setLoaded] = useState<boolean>(false)
  const [progress, setProgress] = useState<number | null>(null)
  const [elapsed, setElapsed] = useState<number>(0)
  const [dismissed, setDismissed] = useState<boolean>(false)
  const [retryKey, setRetryKey] = useState<number>(0)

  const viewerRef = useRef<ModelViewerElement | null>(null)
  const partsRef = useRef<PartDto[]>([])
  const loadStartRef = useRef<number>(Date.now())

  // Camera scanner needs `navigator.mediaDevices` — without it the scan tab is hidden.
  const canScan =
    typeof navigator !== 'undefined' &&
    navigator.mediaDevices !== undefined &&
    typeof navigator.mediaDevices.getUserMedia === 'function'

  const slotOf = useCallback(
    (part: PartDto): string => `hotspot-${contentId ?? 'x'}-${part.id}`,
    [contentId],
  )

  useEffect(() => {
    partsRef.current = parts
  }, [parts])

  // New content = fresh tab + selection + load state (also used by Retry).
  useEffect(() => {
    setTab('viewer')
    setSelected(null)
    setModelError(null)
    setDismissed(false)
    setLoaded(false)
    setProgress(null)
    setElapsed(0)
    loadStartRef.current = Date.now()
  }, [contentId, retryKey])

  // Clear the AR hint automatically.
  useEffect(() => {
    if (!arNote) return
    const timer = setTimeout(() => setArNote(null), 6000)
    return () => clearTimeout(timer)
  }, [arNote])

  // Default ring positions as soon as the parts list changes (used for the
  // initial data-position, since model-viewer reads it once at slot attach).
  useEffect(() => {
    if (parts.length === 0) {
      setAnchors({})
      return
    }
    const next: Record<string, HotspotAnchor> = {}
    parts.forEach((part, i) => {
      const explicit = explicitPosition(part)
      const auto = autoAnchors(parts.length, DEFAULT_CENTER, DEFAULT_SIZE)[i]
      next[slotOf(part)] = {
        position: explicit ?? auto.position,
        normal: explicit ? outwardNormal(explicit, DEFAULT_CENTER) : auto.normal,
      }
    })
    setAnchors(next)
  }, [parts, slotOf])

  /**
   * Once the GLB is loaded, measure its bounding box and re-position every
   * hotspot with model-viewer's `updateHotspot` (data-position is not observed
   * after the element attaches), then centre the camera on the model.
   */
  const placeHotspots = useCallback(
    (el: ModelViewerElement): void => {
      const list = partsRef.current
      if (list.length === 0) return

      let center = DEFAULT_CENTER
      let size = DEFAULT_SIZE
      try {
        if (el.loaded) {
          const dims = el.getDimensions()
          const box = el.getBoundingBoxCenter()
          center = { x: box.x, y: box.y, z: box.z }
          size = {
            x: Math.max(dims.x, 1e-3),
            y: Math.max(dims.y, 1e-3),
            z: Math.max(dims.z, 1e-3),
          }
        }
      } catch {
        // Bounding box unavailable — fall back to the default ring.
      }

      // Frame off-centre models (e.g. a figure standing on the origin) properly.
      if (Math.hypot(center.x, center.y, center.z) > 0.05) {
        const target = `${center.x}m ${center.y}m ${center.z}m`
        if (el.cameraTarget !== target) el.cameraTarget = target
      }

      const auto = autoAnchors(list.length, center, size)
      const next: Record<string, HotspotAnchor> = {}
      list.forEach((part, i) => {
        const explicit = explicitPosition(part)
        const anchor: HotspotAnchor = explicit
          ? { position: explicit, normal: outwardNormal(explicit, center) }
          : auto[i]
        next[slotOf(part)] = anchor
        el.updateHotspot({ name: slotOf(part), position: anchor.position, normal: anchor.normal })
      })
      setAnchors(next)
    },
    [slotOf],
  )

  // Attach load/error/progress listeners; also place immediately if already loaded.
  useEffect(() => {
    const el = viewerRef.current
    if (!el || !detail.data) return
    setLoaded(el.loaded)
    setProgress(el.loaded ? 100 : null)
    const handleLoad = (): void => {
      setModelError(null)
      setLoaded(true)
      setProgress(100)
      placeHotspots(el)
    }
    const handleError = (): void => {
      setLoaded(false)
      setModelError('The 3D model could not be loaded — check its URL or your connection.')
    }
    // model-viewer emits `progress` (detail.totalProgress 0..1) while fetching.
    const handleProgress = (event: Event): void => {
      const total = (event as CustomEvent<{ totalProgress?: number }>).detail?.totalProgress
      if (typeof total === 'number') setProgress(Math.min(99, Math.round(total * 100)))
    }
    el.addEventListener('load', handleLoad)
    el.addEventListener('error', handleError)
    el.addEventListener('progress', handleProgress)
    if (el.loaded) placeHotspots(el)
    return () => {
      el.removeEventListener('load', handleLoad)
      el.removeEventListener('error', handleError)
      el.removeEventListener('progress', handleProgress)
    }
  }, [detail.data, placeHotspots])

  // Elapsed-seconds ticker + stall watchdog while a load is in progress.
  // If nothing arrives within 90 s we surface a real error with a Retry, so
  // the viewer can never sit in a silent "forever loading" state.
  useEffect(() => {
    if (!detail.data || loaded || modelError) return
    loadStartRef.current = Date.now()
    const tick = window.setInterval(() => {
      setElapsed(Math.round((Date.now() - loadStartRef.current) / 1000))
    }, 1000)
    const stall = window.setTimeout(() => {
      setElapsed(Math.round((Date.now() - loadStartRef.current) / 1000))
      setModelError(
        'The 3D model could not be loaded within 90 seconds — this model is large and your connection looks slow. Retry the download now.',
      )
    }, 90_000)
    return () => {
      window.clearInterval(tick)
      window.clearTimeout(stall)
    }
  }, [detail.data, loaded, modelError, retryKey])

  /** Remount <model-viewer> and restart the download from scratch. */
  const handleRetry = (): void => {
    loadStartRef.current = Date.now()
    setElapsed(0)
    setProgress(null)
    setLoaded(false)
    setModelError(null)
    setDismissed(false)
    setRetryKey((k) => k + 1)
  }

  const handleFound = (id: number): void => {
    if (String(id) === contentId) setTab('viewer')
    else navigate(`/ar/${id}`)
  }

  const handleActivateAr = (): void => {
    const el = viewerRef.current
    if (!el) return
    if (!el.canActivateAR) {
      setArNote('AR needs an AR-capable phone (WebXR / Scene Viewer / Quick Look). Explore with camera controls here.')
      return
    }
    el.activateAR().catch(() => {
      setArNote('Could not start AR on this device — the 3D viewer still works.')
    })
  }

  const content = detail.data
  const modelUrl = content?.modelUrl?.trim() ?? ''
  const selectedLabel = selected ? (selected.label ?? selected.partName) : null

  return (
    <div className="space-y-5">
      {/* ---------------------------------------------------------- header */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            to={libraryPath(user?.role)}
            className="text-xs text-slate-500 transition hover:text-cyan-300"
          >
            ← Back to library
          </Link>
          <h1 className="mt-1 truncate text-xl font-semibold tracking-tight">
            {content ? content.title : 'AR lesson'}
          </h1>
          {content?.description && (
            <p className="mt-1 max-w-2xl text-sm text-slate-500">{content.description}</p>
          )}
          {content && (
            <div className="mt-2 flex flex-wrap gap-2 text-[11px]">
              <span className="rounded-full border border-slate-700 px-2.5 py-0.5 text-slate-400">
                {content.subject}
              </span>
              {content.grade && (
                <span className="rounded-full border border-slate-700 px-2.5 py-0.5 text-slate-400">
                  Grade {content.grade}
                </span>
              )}
              <span
                className={`rounded-full px-2.5 py-0.5 font-semibold uppercase tracking-wide ${
                  content.status === 'PUBLISHED'
                    ? 'bg-emerald-500/15 text-emerald-300'
                    : 'bg-amber-500/15 text-amber-300'
                }`}
              >
                {content.status}
              </span>
              {content.markerId && (
                <span className="rounded-full border border-cyan-500/40 bg-cyan-500/10 px-2.5 py-0.5 font-mono text-cyan-300">
                  {content.markerId}
                </span>
              )}
              <span className="rounded-full border border-slate-700 px-2.5 py-0.5 text-slate-400">
                {content.parts.length} part{content.parts.length === 1 ? '' : 's'}
              </span>
            </div>
          )}
        </div>

        {/* ------------------------------------------------------ tab switch */}
        <div className="flex rounded-xl border border-slate-800 bg-slate-900/70 p-1">
          <button
            type="button"
            onClick={() => setTab('viewer')}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
              tab === 'viewer' ? 'bg-cyan-500/15 text-cyan-300' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            🥽 3D viewer
          </button>
          <button
            type="button"
            onClick={() => setTab('scan')}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
              tab === 'scan' ? 'bg-cyan-500/15 text-cyan-300' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            📷 Scan marker
          </button>
        </div>
      </div>

      {/* ----------------------------------------------------------- states */}
      {detail.loading && <Spinner label="Loading AR lesson…" />}

      {!detail.loading && detail.error && (
        <div className="mx-auto max-w-2xl">
          <ErrorBanner error={detail.error} title="AR lesson unavailable" />
          <p className="mt-3 text-center text-sm text-slate-500">
            The lesson could not be fetched from the API — the viewer degrades to this message instead
            of crashing.
          </p>
        </div>
      )}

      {!detail.loading && !detail.error && content && (
        <>
          {tab === 'scan' ? (
            canScan ? (
              <MarkerScanner onContentFound={handleFound} onBackToViewer={() => setTab('viewer')} />
            ) : (
              <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 text-center">
                <span className="text-3xl">📷</span>
                <p className="mt-3 text-sm text-slate-400">
                  Camera access is not available in this browser — showing the 3D viewer only.
                </p>
                <button
                  type="button"
                  onClick={() => setTab('viewer')}
                  className="mt-4 rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800"
                >
                  Back to 3D viewer
                </button>
              </div>
            )
          ) : (
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
              {/* ------------------------------------------------- model-viewer */}
              <div className="relative h-[62vh] min-h-[380px] overflow-hidden rounded-2xl border border-slate-800 bg-slate-950">
                {modelUrl ? (
                  <model-viewer
                    key={retryKey}
                    ref={viewerRef}
                    src={modelUrl}
                    alt={content.title}
                    ar="true"
                    ar-modes="webxr scene-viewer quick-look"
                    camera-controls="true"
                    auto-rotate="true"
                    shadow-intensity="1"
                    exposure="0.9"
                    environment-image="neutral"
                    interaction-prompt="none"
                    loading="eager"
                    reveal="auto"
                    className="h-full w-full"
                  >
                    {parts.map((part, i) => {
                      const slot = slotOf(part)
                      const anchor = anchors[slot] ?? autoAnchors(parts.length, DEFAULT_CENTER, DEFAULT_SIZE)[i]
                      const label = part.label ?? part.partName
                      return (
                        <button
                          key={part.id}
                          type="button"
                          slot={slot}
                          data-position={anchor.position}
                          data-normal={anchor.normal}
                          aria-label={`Show details for ${label}`}
                          onClick={() => setSelected(part)}
                          className={`mv-hotspot ${selected?.id === part.id ? 'mv-hotspot-active' : ''}`}
                        >
                          {label}
                        </button>
                      )
                    })}
                  </model-viewer>
                ) : (
                  <div className="absolute inset-0 flex items-center justify-center bg-slate-950/85 px-6">
                    <p className="max-w-sm text-center text-sm text-amber-200">
                      This lesson has no 3D model URL configured yet.
                    </p>
                  </div>
                )}

                {/* visible load feedback: % + elapsed seconds, never a silent screen */}
                {modelUrl && !loaded && !modelError && (
                  <div className="pointer-events-none absolute left-3 top-3 z-10 rounded-lg border border-cyan-500/30 bg-slate-950/90 px-3 py-2 text-xs text-cyan-200">
                    Loading 3D model… {progress ?? 0}% · {elapsed}s elapsed
                    <span className="ml-2 text-slate-400">(large file — please wait)</span>
                  </div>
                )}

                {/* explicit AR trigger (model-viewer's own FAB sits bottom-right) */}
                <div className="absolute right-3 top-3 flex flex-col items-end gap-2">
                  <button
                    type="button"
                    onClick={handleActivateAr}
                    className="rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-lg transition hover:from-cyan-400 hover:to-blue-500"
                  >
                    View in AR
                  </button>
                  {arNote && (
                    <p className="max-w-[16rem] rounded-lg border border-slate-700 bg-slate-900/95 px-3 py-2 text-right text-xs text-slate-300">
                      {arNote}
                    </p>
                  )}
                </div>

                <p className="pointer-events-none absolute bottom-3 left-3 rounded-lg bg-slate-950/70 px-2.5 py-1 text-[11px] text-slate-400">
                  Drag to rotate · scroll to pinch · tap a label for details
                </p>

                {modelError && !dismissed && (
                  <div className="absolute inset-0 z-20 flex items-center justify-center bg-slate-950/85 px-6">
                    <div className="max-w-sm text-center">
                      <p className="text-sm leading-relaxed text-rose-200">{modelError}</p>
                      <div className="mt-4 flex items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={handleRetry}
                          className="rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-lg transition hover:from-cyan-400 hover:to-blue-500"
                        >
                          ↻ Retry download
                        </button>
                        <button
                          type="button"
                          onClick={() => setDismissed(true)}
                          className="rounded-xl border border-slate-600 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800"
                        >
                          Dismiss
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* after Dismiss the error shrinks to a non-blocking bar, so no
                    overlay can sit over the canvas forever */}
                {modelError && dismissed && (
                  <div className="absolute bottom-12 right-3 z-20 flex max-w-[18rem] items-center gap-2 rounded-lg border border-rose-500/40 bg-slate-950/90 px-3 py-1.5 text-xs text-rose-200">
                    <span className="truncate">3D model failed to load.</span>
                    <button
                      type="button"
                      onClick={handleRetry}
                      className="shrink-0 font-semibold text-cyan-300 transition hover:text-cyan-200"
                    >
                      Retry
                    </button>
                  </div>
                )}
              </div>

              {/* --------------------------------------------------- parts panel */}
              <aside className="flex max-h-[62vh] flex-col gap-4 overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
                <div>
                  <h2 className="text-sm font-semibold uppercase tracking-widest text-slate-500">
                    Model parts
                  </h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Tap a hotspot on the model — or pick a part here.
                  </p>
                </div>

                {parts.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-slate-700 p-4 text-sm text-slate-500">
                    This lesson has no annotated parts yet.
                  </p>
                ) : (
                  <ul className="flex flex-wrap gap-2">
                    {parts.map((part) => (
                      <li key={part.id}>
                        <button
                          type="button"
                          onClick={() => setSelected(part)}
                          className={`rounded-lg border px-2.5 py-1.5 text-xs font-medium transition ${
                            selected?.id === part.id
                              ? 'border-cyan-400/60 bg-cyan-500/15 text-cyan-200'
                              : 'border-slate-700 text-slate-400 hover:border-slate-500 hover:text-slate-200'
                          }`}
                        >
                          {part.label ?? part.partName}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                {selected ? (
                  <div className="rounded-xl border border-cyan-500/30 bg-cyan-500/5 p-4">
                    <p className="text-[10px] font-semibold uppercase tracking-widest text-cyan-400">
                      Selected part
                    </p>
                    <h3 className="mt-1 text-sm font-semibold text-slate-100">{selectedLabel}</h3>
                    <p className="mt-0.5 font-mono text-[11px] text-slate-500">{selected.partName}</p>
                    <p className="mt-2 text-sm leading-relaxed text-slate-300">
                      {selected.explanation ?? 'No explanation has been written for this part yet.'}
                    </p>
                  </div>
                ) : (
                  parts.length > 0 && (
                    <p className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 text-sm text-slate-500">
                      Nothing selected — choose a part to read its explanation.
                    </p>
                  )
                )}

                <div className="mt-auto rounded-xl border border-slate-800 bg-slate-950/60 p-3 text-xs text-slate-500">
                  <p className="font-mono text-slate-400">{content.modelUrl}</p>
                  <p className="mt-1">v{content.version} · by {content.createdBy ?? 'unknown'}</p>
                </div>
              </aside>
            </div>
          )}
        </>
      )}
    </div>
  )
}
