import { Link, useParams } from 'react-router-dom'

/**
 * Placeholder for the server-graded quiz — lands with the AR viewer in TASK 6.
 * Route: /quiz/:assignmentId
 */
export default function QuizStub() {
  const { assignmentId } = useParams()

  return (
    <div className="mx-auto max-w-2xl">
      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 text-center">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400/20 to-cyan-500/20 text-2xl">
          🧠
        </div>
        <h1 className="text-lg font-semibold tracking-tight">Quiz arrives in TASK 6</h1>
        <p className="mt-2 text-sm leading-relaxed text-slate-400">
          Questions will be fetched from <code className="rounded bg-slate-800 px-1.5 py-0.5 text-cyan-300">GET /api/ar-content/&#123;id&#125;</code>{' '}
          and graded server-side by <code className="rounded bg-slate-800 px-1.5 py-0.5 text-cyan-300">POST /api/quiz/submit</code> —
          answers never travel to the browser.
          <br />
          <span className="text-slate-200">Assignment #{assignmentId ?? '—'}</span>
        </p>
        <Link
          to="/student"
          className="mt-6 inline-block rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800"
        >
          ← Back to dashboard
        </Link>
      </div>
    </div>
  )
}
