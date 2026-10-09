import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { AssignmentDto, ContentDetail } from '../api/types'
import { apiFetch } from '../api/client'
import { useApi } from '../api/useApi'
import ErrorBanner from '../components/ErrorBanner'
import Spinner from '../components/Spinner'

interface Option {
  key: string
  text: string
}

interface AnswerResult {
  questionId: number
  selected: string
  correct: boolean
  correctOption: string
  explanation: string
  topic: string
}

interface SubmitResponse {
  score: number
  totalQuestions: number
  percentage: number
  attempts: number
  bestScore: number
  relatedContentTitle: string
  results: AnswerResult[]
}

/** Server options arrive as a flat list ["a","Right atrium","b","Left atrium",...]. */
function parseOptions(options: string[]): Option[] {
  const out: Option[] = []
  for (let i = 0; i + 1 < options.length; i += 2) {
    out.push({ key: String(options[i]).trim().toLowerCase(), text: options[i + 1] })
  }
  if (out.length === 0 && options.length > 0) {
    options.forEach((o, idx) => out.push({ key: String.fromCharCode(97 + idx), text: o }))
  }
  return out
}

/**
 * Real quiz flow (replaces the T5 stub): server-graded, one question at a time.
 * The score shown is ALWAYS the server's SubmitResponse — never a client guess.
 */
export default function QuizPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>()
  const assignments = useApi<AssignmentDto[]>('/api/assignments/student')

  const assignment = useMemo(
    () => (assignments.data ?? []).find((a) => String(a.id) === assignmentId),
    [assignments.data, assignmentId],
  )
  const content = useApi<ContentDetail | null>(assignment ? `/api/ar-content/${assignment.contentId}` : null)

  const [phase, setPhase] = useState<'intro' | 'quiz' | 'result' | 'error'>('intro')
  const [phaseError, setPhaseError] = useState<string | null>(null)
  const [index, setIndex] = useState(0)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SubmitResponse | null>(null)
  const [startedAt] = useState(() => Date.now())

  const questions = content.data?.questions ?? []
  const optionsByQ = useMemo(() => {
    const map: Record<number, Option[]> = {}
    for (const q of questions) map[q.id] = parseOptions(q.options)
    return map
  }, [questions])

  async function submit() {
    if (!assignment) return
    setSubmitting(true)
    setPhaseError(null)
    try {
      const payload = {
        assignmentId: assignment.id,
        contentId: assignment.contentId,
        answers: Object.entries(answers).map(([questionId, selected]) => ({
          questionId: Number(questionId),
          selected,
        })),
        timeTakenSeconds: Math.max(1, Math.round((Date.now() - startedAt) / 1000)),
      }
      const res = await apiFetch<SubmitResponse>('/api/quiz/submit', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      setResult(res)
      setPhase('result')
    } catch (err) {
      setPhaseError(err instanceof Error ? err.message : String(err))
      setPhase('error')
    } finally {
      setSubmitting(false)
    }
  }

  if (assignments.loading) return <Spinner label="Loading assignments…" />
  if (assignments.error) return <ErrorBanner error={assignments.error} title="Assignments unavailable" />
  if (!assignment) {
    return (
      <div className="mx-auto max-w-2xl">
        <ErrorBanner
          error={new Error(`Assignment #${assignmentId} not found.`)}
          title="Unknown assignment"
        />
        <Link to="/student" className="mt-4 inline-block rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
          ← Back to dashboard
        </Link>
      </div>
    )
  }

  if (content.loading) return <Spinner label="Loading questions…" />
  if (content.error) return <ErrorBanner error={content.error} title="Questions unavailable" />

  // ---- result screen ----
  if (phase === 'result' && result) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 text-center">
          <p className="text-5xl font-bold tracking-tight">
            <span className={result.percentage >= 70 ? 'text-emerald-400' : 'text-amber-300'}>
              {result.percentage}%
            </span>
          </p>
          <h1 className="mt-2 text-lg font-semibold text-slate-100">
            {result.score} / {result.totalQuestions} correct
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            {assignment.title} · attempt #{result.attempts} · best {result.bestScore}/{result.totalQuestions}
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <button
              onClick={() => {
                setAnswers({})
                setIndex(0)
                setPhase('quiz')
              }}
              className="rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2 text-sm font-semibold text-white hover:from-cyan-400 hover:to-blue-500"
            >
              Retry quiz
            </button>
            <Link to="/student" className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
              ← Back to dashboard
            </Link>
          </div>
        </div>

        <div className="space-y-3">
          {result.results.map((r) => (
            <div
              key={r.questionId}
              className={`rounded-2xl border p-4 ${
                r.correct ? 'border-emerald-500/30 bg-emerald-500/5' : 'border-rose-500/30 bg-rose-500/5'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <p className="text-sm font-medium text-slate-200">{questions.find((q) => q.id === r.questionId)?.questionText ?? `Question ${r.questionId}`}</p>
                <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase ${r.correct ? 'bg-emerald-500/20 text-emerald-300' : 'bg-rose-500/20 text-rose-300'}`}>
                  {r.correct ? 'Correct' : 'Wrong'}
                </span>
              </div>
              <p className="mt-1 text-xs text-slate-400">
                {r.correct
                  ? `You picked ${r.selected.toUpperCase()}.`
                  : `You picked ${r.selected.toUpperCase()} — correct is ${r.correctOption.toUpperCase()}.`}
              </p>
              {r.explanation && <p className="mt-2 text-xs leading-relaxed text-slate-300">{r.explanation}</p>}
              {r.topic && <p className="mt-2 text-[10px] font-semibold uppercase tracking-wide text-cyan-400">{r.topic}</p>}
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (phase === 'error') {
    return (
      <div className="mx-auto max-w-2xl">
        <ErrorBanner error={new Error(phaseError ?? 'Submission failed')} title="Could not submit quiz" />
        <div className="mt-4 flex gap-3">
          <button onClick={() => setPhase('quiz')} className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
            ← Back to questions
          </button>
          <Link to="/student" className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
            Dashboard
          </Link>
        </div>
      </div>
    )
  }

  // ---- intro ----
  if (phase === 'intro') {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400/20 to-cyan-500/20 text-2xl">🧠</div>
          <h1 className="text-lg font-semibold tracking-tight text-slate-100">{assignment.title}</h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-400">
            {assignment.instructions ?? 'Answer the questions. Grading is server-side.'}
          </p>
          <div className="mt-4 flex justify-center gap-2 text-xs text-slate-500">
            <span className="rounded-full border border-slate-700 px-2.5 py-1">{questions.length} questions</span>
            <span className="rounded-full border border-slate-700 px-2.5 py-1">{assignment.contentTitle}</span>
            <span className="rounded-full border border-slate-700 px-2.5 py-1">{assignment.className}</span>
          </div>
          <button
            onClick={() => questions.length > 0 && setPhase('quiz')}
            disabled={questions.length === 0}
            className="mt-6 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-5 py-2.5 text-sm font-semibold text-white hover:from-cyan-400 hover:to-blue-500 disabled:opacity-40"
          >
            {questions.length === 0 ? 'No questions in this lesson' : 'Start quiz'}
          </button>
        </div>
        <Link to="/student" className="mt-4 inline-block rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
          ← Back to dashboard
        </Link>
      </div>
    )
  }

  // ---- quiz ----
  const q = questions[index]
  if (!q) {
    // no questions at all
    return (
      <div className="mx-auto max-w-2xl">
        <ErrorBanner error={new Error('This lesson has no questions yet.')} title="Nothing to quiz" />
        <Link to="/student" className="mt-4 inline-block rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800">
          ← Back to dashboard
        </Link>
      </div>
    )
  }
  const options = optionsByQ[q.id] ?? []
  const selected = answers[q.id] ?? null
  const answeredCount = Object.keys(answers).length
  const isLast = index === questions.length - 1

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>
          Question {index + 1} of {questions.length}
        </span>
        <span>
          {answeredCount} answered · {messagesSafe()}
        </span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
        <div
          className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-blue-500 transition-all"
          style={{ width: `${(answeredCount / Math.max(1, questions.length)) * 100}%` }}
        />
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-6">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-cyan-400">{q.topic ?? 'Lesson'}</p>
        <h2 className="mt-1 text-base font-medium leading-relaxed text-slate-100">{q.questionText}</h2>

        <div className="mt-4 space-y-2">
          {options.map((opt) => {
            const isSelected = selected === opt.key
            return (
              <button
                key={opt.key}
                onClick={() => setAnswers((a) => ({ ...a, [q.id]: opt.key }))}
                className={`flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left text-sm transition ${
                  isSelected
                    ? 'border-cyan-500/60 bg-cyan-500/10 text-cyan-100'
                    : 'border-slate-800 bg-slate-950/50 text-slate-300 hover:border-slate-600'
                }`}
              >
                <span
                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-xs font-bold ${
                    isSelected ? 'bg-cyan-500 text-white' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {opt.key.toUpperCase()}
                </span>
                {opt.text}
              </button>
            )
          })}
        </div>
      </div>

      <div className="flex items-center justify-between">
        <button
          onClick={() => setIndex((i) => Math.max(0, i - 1))}
          disabled={index === 0}
          className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-30"
        >
          ← Previous
        </button>
        {isLast ? (
          <button
            onClick={submit}
            disabled={submitting || answeredCount < questions.length}
            className="rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-600 px-5 py-2.5 text-sm font-semibold text-white hover:from-emerald-400 hover:to-cyan-500 disabled:opacity-40"
          >
            {submitting ? 'Submitting…' : 'Submit answers'}
          </button>
        ) : (
          <button
            onClick={() => setIndex((i) => Math.min(questions.length - 1, i + 1))}
            disabled={!selected}
            className="rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-5 py-2.5 text-sm font-semibold text-white hover:from-cyan-400 hover:to-blue-500 disabled:opacity-40"
          >
            Next →
          </button>
        )}
      </div>
      <p className="text-center text-xs text-slate-600">You may change answers before submitting.</p>
    </div>
  )

  function messagesSafe() {
    return questions.length > 0 ? 'choose carefully' : 'no questions'
  }
}