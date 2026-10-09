import { useEffect, useRef, useState } from 'react'
import type { ClassDto } from '../api/types'
import { apiFetch } from '../api/client'
import { useApi } from '../api/useApi'
import ErrorBanner from '../components/ErrorBanner'
import Spinner from '../components/Spinner'

interface LiveSession {
  sessionId: number
  joinCode: string
  className: string
  assignmentTitle: string | null
  activeSession: boolean
  totalStudents: number
  joined: number
  active: number
  inProgress: number
  notStarted: number
  completed: number
  students: Array<{
    studentId: number
    name: string
    status: string
    completion: number
    lastHeartbeat: string | null
  }>
}

function statusDot(status: string) {
  const map: Record<string, string> = {
    ACTIVE: 'bg-emerald-400',
    IN_PROGRESS: 'bg-cyan-400',
    COMPLETED: 'bg-violet-400',
    NOT_STARTED: 'bg-slate-500',
    ONLINE: 'bg-emerald-400',
  }
  return <span className={`inline-block h-2 w-2 rounded-full ${map[status] ?? 'bg-slate-500'}`} />
}

/**
 * Teacher's live classroom: create a session -> share the join code ->
 * watch student heartbeats -> end the session.
 */
export default function TeacherLivePage() {
  const classes = useApi<ClassDto[]>('/api/classes')
  const [classId, setClassId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [session, setSession] = useState<{ joinCode: string; sessionId: number } | null>(null)
  const [state, setState] = useState<LiveSession | null>(null)
  const [copied, setCopied] = useState(false)
  const pollRef = useRef<number | null>(null)

  useEffect(() => {
    if (classId === null && classes.data && classes.data.length > 0) {
      setClassId(classes.data[0].id)
    }
  }, [classes.data, classId])

  // Poll session state every 5 s while a session is live.
  useEffect(() => {
    if (!session) return
    const tick = async () => {
      try {
        const s = await apiFetch<LiveSession>(`/api/live/sessions/${session.sessionId}`)
        setState(s)
        if (!s.activeSession) {
          if (pollRef.current !== null) window.clearInterval(pollRef.current)
          pollRef.current = null
          setSession(null)
          setState(null)
        }
      } catch {
        /* transient poll error — keep the session, next tick retries */
      }
    }
    void tick()
    pollRef.current = window.setInterval(tick, 5000)
    return () => {
      if (pollRef.current !== null) window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [session])

  async function createSession() {
    if (classId === null || creating) return
    setCreating(true)
    setErr(null)
    try {
      const res = await apiFetch<{ joinCode: string; sessionId: number; className: string }>('/api/live/sessions', {
        method: 'POST',
        body: JSON.stringify({ classId }),
      })
      setSession({ joinCode: res.joinCode, sessionId: res.sessionId })
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setCreating(false)
    }
  }

  async function endSession() {
    if (!session) return
    try {
      await apiFetch(`/api/live/sessions/${session.sessionId}/end`, { method: 'POST' })
    } catch {
      /* even if end fails the poll will notice it deactivated */
    }
    if (pollRef.current !== null) window.clearInterval(pollRef.current)
    pollRef.current = null
    setSession(null)
    setState(null)
  }

  async function copyCode() {
    if (!session) return
    try {
      await navigator.clipboard.writeText(session.joinCode)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard can be blocked on http:// — fall back to manual copy */
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Live classroom</h1>
        <p className="text-sm text-slate-500">Run a real-time AR lesson session — students join with a code.</p>
      </div>

      {classes.loading && <Spinner label="Loading classes…" />}
      {err && <ErrorBanner error={new Error(err)} title="Live session error" />}

      {!session && (
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-6">
          <label className="flex flex-col gap-1 text-xs text-slate-400">
            <span>Class</span>
            <select
              value={classId ?? ''}
              onChange={(e) => setClassId(Number(e.target.value))}
              className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
            >
              {(classes.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.studentCount} students)
                </option>
              ))}
            </select>
          </label>
          <button
            onClick={createSession}
            disabled={creating || classId === null}
            className="mt-4 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:from-cyan-400 hover:to-blue-500 disabled:opacity-40"
          >
            {creating ? 'Starting…' : 'Start live session'}
          </button>
        </div>
      )}

      {session && (
        <>
          <div className="rounded-2xl border border-cyan-500/30 bg-slate-900/70 p-6 text-center">
            <p className="text-xs font-semibold uppercase tracking-widest text-slate-500">Students join with code</p>
            <p className="mt-1 text-5xl font-bold tracking-[0.35em] text-cyan-300">{session.joinCode}</p>
            <button
              onClick={copyCode}
              className="mt-4 rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:border-cyan-500/50 hover:text-cyan-300"
            >
              {copied ? '✓ Copied' : 'Copy code'}
            </button>
            <div className="mt-4 flex justify-center gap-2 text-xs text-slate-400">
              <span className="rounded-full border border-slate-700 px-2.5 py-1">👥 {state?.joined ?? 0}/{state?.totalStudents ?? '—'} joined</span>
              <span className="rounded-full border border-slate-700 px-2.5 py-1">🟢 {state?.active ?? 0} active</span>
            </div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-200">Student activity</h2>
            {!state || state.students.length === 0 ? (
              <p className="text-sm text-slate-500">Waiting for students to join…</p>
            ) : (
              <ul className="space-y-2">
                {state.students.map((s) => (
                  <li key={s.studentId} className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/50 px-4 py-2.5 text-sm">
                    <span className="flex items-center gap-2 text-slate-200">
                      {statusDot(s.status)} {s.name}
                    </span>
                    <span className="flex items-center gap-3 text-xs text-slate-400">
                      <span className="uppercase">{s.status.replace('_', ' ')}</span>
                      <span className="tabular-nums text-slate-300">{s.completion}%</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <button
            onClick={endSession}
            className="rounded-xl border border-rose-500/50 px-4 py-2 text-sm font-medium text-rose-300 transition hover:bg-rose-500/10"
          >
            End session
          </button>
        </>
      )}
    </div>
  )
}