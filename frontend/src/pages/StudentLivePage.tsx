import { useEffect, useRef, useState } from 'react'
import { apiFetch } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'

interface LiveState {
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
  students: Array<{ studentId: number; name: string; status: string; completion: number; lastHeartbeat: string | null }>
}

/**
 * Student's live classroom: enter the teacher's code -> join ->
 * send a heartbeat every 20 s until the session ends.
 */
export default function StudentLivePage() {
  const [code, setCode] = useState('')
  const [joining, setJoining] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [sessionId, setSessionId] = useState<number | null>(null)
  const [state, setState] = useState<LiveState | null>(null)
  const heartbeatRef = useRef<number | null>(null)

  useEffect(() => {
    if (sessionId === null) return
    const pump = async () => {
      try {
        const s = await apiFetch<LiveState>(`/api/live/sessions/${sessionId}`)
        setState(s)
        if (!s.activeSession && heartbeatRef.current !== null) {
          window.clearInterval(heartbeatRef.current)
          heartbeatRef.current = null
          setSessionId(null)
          setState(null)
        }
      } catch {
        /* transient — retry next tick */
      }
    }
    void pump()
    heartbeatRef.current = window.setInterval(pump, 20000)
    return () => {
      if (heartbeatRef.current !== null) window.clearInterval(heartbeatRef.current)
      heartbeatRef.current = null
    }
  }, [sessionId])

  async function join() {
    const joinCode = code.trim().toUpperCase()
    if (!joinCode || joining) return
    setJoining(true)
    setErr(null)
    try {
      const res = await apiFetch<{ sessionId: number }>(`/api/live/sessions/${joinCode}/join`, { method: 'POST' })
      setSessionId(res.sessionId)
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setJoining(false)
    }
  }

  if (sessionId === null) {
    return (
      <div className="mx-auto max-w-md">
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400/20 to-cyan-500/20 text-2xl">🛰️</div>
          <h1 className="text-lg font-semibold tracking-tight">Join live classroom</h1>
          <p className="mt-2 text-sm text-slate-400">Enter the code your teacher shared to join an AR session.</p>
          <input
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            onKeyDown={(e) => e.key === 'Enter' && join()}
            placeholder="ABC123"
            maxLength={8}
            className="mt-5 w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-center text-2xl font-bold tracking-[0.4em] text-cyan-300 placeholder:text-slate-700 focus:border-cyan-500/60 focus:outline-none"
          />
          <button
            onClick={join}
            disabled={joining || code.trim().length < 3}
            className="mt-5 w-full rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 py-2.5 text-sm font-semibold text-white transition hover:from-cyan-400 hover:to-blue-500 disabled:opacity-40"
          >
            {joining ? 'Joining…' : 'Join session'}
          </button>
          {err && <div className="mt-4"><ErrorBanner error={new Error(err)} title="Could not join" /></div>}
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md space-y-4">
      <div className="rounded-2xl border border-cyan-500/30 bg-slate-900/70 p-8 text-center">
        <p className="text-xs font-semibold uppercase tracking-widest text-slate-500">In live session</p>
        <h1 className="mt-1 text-lg font-semibold text-slate-100">{state?.className ?? 'Classroom'}</h1>
        <p className="mt-1 text-sm text-slate-400">{state?.assignmentTitle ?? 'Free exploration'}</p>
        <div className="mt-4 flex justify-center gap-2 text-xs text-slate-400">
          <span className="rounded-full border border-slate-700 px-2.5 py-1">🟢 {state?.active ?? 0} active now</span>
          <span className="rounded-full border border-slate-700 px-2.5 py-1">👥 {state?.joined ?? 0}/{state?.totalStudents ?? '—'} joined</span>
        </div>
        <p className="mt-4 text-[11px] text-slate-600">Heartbeat active — your progress reaches the teacher automatically.</p>
      </div>
    </div>
  )
}