import { useEffect, useRef, useState } from 'react'
import jsQR from 'jsqr'
import { apiFetch } from '../api/client'
import type { ContentDetail } from '../api/types'

/**
 * MarkerScanner — live camera tab of the AR viewer.
 *
 * - `getUserMedia({ video: { facingMode: "environment" } })` (rear camera on phones)
 * - frames are drawn to an offscreen canvas and decoded with jsQR at ~8 fps
 * - a decoded value is looked up via `GET /api/ar-content/marker/{markerId}`;
 *   a hit calls `onContentFound` (the page swaps to the 3D viewer), a miss keeps
 *   scanning with a friendly "keep scanning" note
 * - every MediaStreamTrack is stopped when the tab unmounts (no leaked camera)
 */

export type ScanPhase = 'starting' | 'scanning' | 'checking' | 'matched' | 'no-match' | 'denied'

interface MarkerScannerProps {
  /** A marker resolved to a lesson — the page loads it in the viewer. */
  onContentFound: (contentId: number) => void
  /** Camera denied/unavailable — the page falls back to the 3D viewer. */
  onBackToViewer: () => void
}

/** Human-friendly camera failure text (all states keep the viewer usable). */
function cameraErrorMessage(err: unknown): string {
  const name = err instanceof Error ? err.name : ''
  switch (name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
      return 'Camera permission denied — the 3D viewer still works.'
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return 'No camera was found on this device — the 3D viewer still works.'
    case 'NotReadableError':
    case 'TrackStartError':
      return 'Your camera is being used by another app — the 3D viewer still works.'
    case 'SecurityError':
      return 'Camera access is blocked here (HTTPS or localhost required) — the 3D viewer still works.'
    default:
      return 'Camera unavailable — the 3D viewer still works.'
  }
}

function shortValue(value: string): string {
  return value.length > 40 ? `${value.slice(0, 40)}…` : value
}

const PHASE_LABEL: Record<ScanPhase, string> = {
  starting: 'Starting camera…',
  scanning: 'Scanning…',
  checking: 'Checking marker…',
  matched: 'Marker found',
  'no-match': 'Keep scanning',
  denied: 'Camera unavailable',
}

const PHASE_STYLE: Record<ScanPhase, string> = {
  starting: 'border-slate-600 bg-slate-900/90 text-slate-300',
  scanning: 'border-cyan-400/50 bg-cyan-950/90 text-cyan-200',
  checking: 'border-amber-400/50 bg-amber-950/90 text-amber-200',
  matched: 'border-emerald-400/50 bg-emerald-950/90 text-emerald-200',
  'no-match': 'border-violet-400/50 bg-violet-950/90 text-violet-200',
  denied: 'border-rose-400/50 bg-rose-950/90 text-rose-200',
}

/** Ignore the same QR payload for 5s so one marker is not looked up every frame. */
const CODE_COOLDOWN_MS = 5000
/** Decode at most every 120ms (~8 fps) — plenty for a handheld marker. */
const DECODE_INTERVAL_MS = 120

export default function MarkerScanner({ onContentFound, onBackToViewer }: MarkerScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [phase, setPhase] = useState<ScanPhase>('starting')
  const [note, setNote] = useState('Point the camera at a printed AR marker (e.g. ARU-HELMET-101).')

  // Keep the latest callbacks without re-running the camera effect.
  const foundRef = useRef(onContentFound)
  useEffect(() => {
    foundRef.current = onContentFound
  }, [onContentFound])

  useEffect(() => {
    const video = videoRef.current
    if (!video) return

    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) {
      setPhase('denied')
      setNote('This browser cannot read camera frames — the 3D viewer still works.')
      return
    }

    let stream: MediaStream | null = null
    let rafId = 0
    let destroyed = false
    let handled = false
    let lastDecode = 0
    const cooldown = new Map<string, number>()

    const handleCode = async (value: string): Promise<void> => {
      const now = performance.now()
      const lastSeen = cooldown.get(value) ?? 0
      if (now - lastSeen < CODE_COOLDOWN_MS) return
      cooldown.set(value, now)

      setPhase('checking')
      setNote(`Code detected: ${shortValue(value)} — looking up the lesson…`)
      try {
        const content = await apiFetch<ContentDetail>(`/api/ar-content/marker/${encodeURIComponent(value)}`)
        if (destroyed) return
        if (content.markerId && content.markerId !== value) {
          setPhase('no-match')
          setNote('That code is not a lesson marker — keep scanning.')
          return
        }
        handled = true
        setPhase('matched')
        setNote(`Marker ${shortValue(value)} matched — opening the lesson…`)
        foundRef.current(content.id)
      } catch {
        if (destroyed) return
        setPhase('no-match')
        setNote(`No lesson matches “${shortValue(value)}” yet — keep scanning.`)
      }
    }

    const decodeFrame = (time: number): void => {
      rafId = requestAnimationFrame(decodeFrame)
      if (handled || time - lastDecode < DECODE_INTERVAL_MS) return
      lastDecode = time
      if (video.readyState < 2) return
      const width = video.videoWidth
      const height = video.videoHeight
      if (!width || !height) return
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width
        canvas.height = height
      }
      ctx.drawImage(video, 0, 0, width, height)
      const frame = ctx.getImageData(0, 0, width, height)
      const code = jsQR(frame.data, width, height, { inversionAttempts: 'attemptBoth' })
      if (code && code.data) void handleCode(code.data)
    }

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' } })
      .then((media) => {
        if (destroyed) {
          media.getTracks().forEach((track) => track.stop())
          return
        }
        stream = media
        video.srcObject = media
        return video.play()
      })
      .then(() => {
        if (destroyed) return
        setPhase('scanning')
        setNote('Scanning — hold the marker steady inside the frame.')
        rafId = requestAnimationFrame(decodeFrame)
      })
      .catch((err: unknown) => {
        if (destroyed) return
        setPhase('denied')
        setNote(cameraErrorMessage(err))
      })

    return () => {
      destroyed = true
      cancelAnimationFrame(rafId)
      if (stream) stream.getTracks().forEach((track) => track.stop())
      video.srcObject = null
    }
  }, [])

  return (
    <div className="space-y-4">
      <div className="relative aspect-[16/10] overflow-hidden rounded-2xl border border-slate-800 bg-black">
        <video ref={videoRef} className="h-full w-full object-cover" playsInline muted autoPlay />
        {/* framing guide */}
        <div className="pointer-events-none absolute inset-8 rounded-xl border-2 border-dashed border-cyan-400/40" />
        <div
          className={`absolute left-3 top-3 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold backdrop-blur ${PHASE_STYLE[phase]}`}
        >
          <span
            className={`h-2 w-2 rounded-full ${
              phase === 'scanning'
                ? 'animate-pulse bg-cyan-300'
                : phase === 'denied'
                  ? 'bg-rose-300'
                  : 'bg-current'
            }`}
          />
          {PHASE_LABEL[phase]}
        </div>
        {phase === 'denied' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-slate-950/85 px-6 text-center">
            <span className="text-3xl">📷</span>
            <p className="max-w-sm text-sm text-slate-300">{note}</p>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-400">{phase !== 'denied' ? note : 'Showing the 3D viewer instead.'}</p>
        <button
          type="button"
          onClick={onBackToViewer}
          className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800"
        >
          Back to 3D viewer
        </button>
      </div>
    </div>
  )
}
