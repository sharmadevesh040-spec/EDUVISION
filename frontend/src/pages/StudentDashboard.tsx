import { Link } from 'react-router-dom'
import { useApi } from '../api/useApi'
import type { AssignmentDto, ContentSummary, ProgressDto, QuizResultRow } from '../api/types'
import StatCard from '../components/StatCard'
import DataTable, { type Column } from '../components/DataTable'
import ErrorBanner from '../components/ErrorBanner'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'
import { avg, fmtDate, fmtDuration, fmtRelative } from '../utils/format'

function statusPill(status: string) {
  const map: Record<string, string> = {
    COMPLETED: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
    IN_PROGRESS: 'bg-cyan-500/15 text-cyan-300 ring-cyan-500/30',
    NOT_STARTED: 'bg-slate-500/15 text-slate-300 ring-slate-500/30',
  }
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ring-1 ring-inset ${
        map[status] ?? map.NOT_STARTED
      }`}
    >
      {status.replace('_', ' ')}
    </span>
  )
}

function ProgressBar({ value }: { value: number }) {
  const pct = Math.max(0, Math.min(100, value))
  return (
    <div className="flex items-center justify-end gap-2">
      <div className="h-1.5 w-20 overflow-hidden rounded-full bg-slate-800">
        <div
          className={`h-full rounded-full ${pct >= 100 ? 'bg-emerald-400' : 'bg-cyan-400'}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="tabular-nums text-xs text-slate-400">{pct}%</span>
    </div>
  )
}

/** /student — assignments, progress and quiz results from the live API. */
export default function StudentDashboard() {
  const assignments = useApi<AssignmentDto[]>('/api/assignments/student')
  const progress = useApi<ProgressDto[]>('/api/progress/me')
  const quiz = useApi<QuizResultRow[]>('/api/quiz/results/me')
  const contents = useApi<ContentSummary[]>('/api/ar-content')

  const assignmentRows = assignments.data ?? []
  const progressRows = progress.data ?? []
  const quizRows = quiz.data ?? []
  const completed = assignmentRows.filter((a) => a.status === 'COMPLETED').length
  const avgScore = quizRows.length > 0 ? avg(quizRows.map((q) => q.percentage)) : null
  const timeSpent = progressRows.reduce((sum, p) => sum + p.timeSpentSeconds, 0)

  const assignmentColumns: Column<AssignmentDto>[] = [
    {
      key: 'title',
      header: 'Assignment',
      render: (a) => (
        <div>
          <p className="font-medium text-slate-200">{a.title}</p>
          <p className="text-xs text-slate-500">
            {a.className} · {a.contentTitle}
          </p>
        </div>
      ),
    },
    { key: 'deadline', header: 'Deadline', render: (a) => fmtRelative(a.deadline) },
    { key: 'status', header: 'Status', render: (a) => statusPill(a.status) },
    { key: 'completion', header: 'Progress', render: (a) => <ProgressBar value={a.completion} /> },
    {
      key: 'go',
      header: '',
      align: 'right',
      render: (a) => (
        <div className="flex justify-end gap-2">
          <Link
            to={`/ar/${a.contentId}`}
            className="rounded-lg border border-cyan-500/40 px-2.5 py-1 text-xs font-medium text-cyan-300 transition hover:bg-cyan-500/10"
          >
            Open AR
          </Link>
          <Link
            to={`/quiz/${a.id}`}
            className="rounded-lg border border-slate-700 px-2.5 py-1 text-xs font-medium text-slate-300 transition hover:bg-slate-800"
          >
            Quiz
          </Link>
        </div>
      ),
    },
  ]

  const progressColumns: Column<ProgressDto>[] = [
    {
      key: 'content',
      header: 'Lesson',
      render: (p) => (
        <div>
          <p className="font-medium text-slate-200">{p.contentTitle}</p>
          <p className="text-xs text-slate-500">{p.subject}</p>
        </div>
      ),
    },
    { key: 'completion', header: 'Completion', align: 'right', render: (p) => `${p.completion}%` },
    { key: 'sessions', header: 'Sessions', align: 'right', render: (p) => p.sessionCount },
    { key: 'time', header: 'Time', align: 'right', render: (p) => fmtDuration(p.timeSpentSeconds) },
    { key: 'last', header: 'Last accessed', render: (p) => <span className="text-slate-400">{fmtRelative(p.lastAccessed)}</span> },
  ]

  const quizColumns: Column<QuizResultRow>[] = [
    { key: 'content', header: 'Lesson', render: (q) => <span className="font-medium text-slate-200">{q.contentTitle}</span> },
    {
      key: 'score',
      header: 'Score',
      align: 'right',
      render: (q) => (
        <span className="tabular-nums">
          {q.score}/{q.totalQuestions}
        </span>
      ),
    },
    {
      key: 'pct',
      header: 'Result',
      align: 'right',
      render: (q) => (
        <span className={q.percentage >= 70 ? 'text-emerald-300' : 'text-amber-300'}>{q.percentage}%</span>
      ),
    },
    { key: 'attempts', header: 'Attempts', align: 'right', render: (q) => q.attempts },
    { key: 'at', header: 'Submitted', render: (q) => <span className="text-slate-400">{fmtDate(q.submittedAt)}</span> },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">My learning dashboard</h1>
        <p className="text-sm text-slate-500">Assignments, AR progress and quiz results — live from the API.</p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          label="Assignments"
          value={assignments.loading ? '' : assignmentRows.length}
          loading={assignments.loading}
          hint="GET /api/assignments/student"
        />
        <StatCard
          label="Completed"
          value={assignments.loading ? '' : completed}
          loading={assignments.loading}
          accent="emerald"
          hint="Status COMPLETED"
        />
        <StatCard
          label="Avg quiz score"
          value={quiz.loading ? '' : avgScore === null ? '—' : `${avgScore}%`}
          loading={quiz.loading}
          accent="violet"
          hint="GET /api/quiz/results/me"
        />
        <StatCard
          label="Time on task"
          value={progress.loading ? '' : fmtDuration(timeSpent)}
          loading={progress.loading}
          accent="amber"
          hint="GET /api/progress/me"
        />
      </div>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        <h2 className="mb-4 text-sm font-semibold text-slate-200">My assignments</h2>
        {assignments.loading && <Spinner label="Loading assignments…" />}
        {!assignments.loading && assignments.error && (
          <ErrorBanner error={assignments.error} title="Assignments unavailable" />
        )}
        {!assignments.loading && !assignments.error && (
          <DataTable
            columns={assignmentColumns}
            rows={assignmentRows}
            rowKey={(a) => a.id}
            empty={
              <EmptyState
                icon="📋"
                title="No assignments yet"
                hint="When a teacher assigns an AR lesson to your class it will appear here."
              />
            }
          />
        )}
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-200">Learning progress</h2>
          {progress.loading && <Spinner label="Loading progress…" />}
          {!progress.loading && progress.error && <ErrorBanner error={progress.error} title="Progress unavailable" />}
          {!progress.loading && !progress.error && (
            <DataTable
              columns={progressColumns}
              rows={progressRows}
              rowKey={(p) => p.id}
              empty={
                <EmptyState
                  icon="⏱️"
                  title="No AR sessions yet"
                  hint="Open an AR lesson and your time, sessions and completion land here."
                />
              }
            />
          )}
        </section>

        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-200">Quiz results</h2>
          {quiz.loading && <Spinner label="Loading results…" />}
          {!quiz.loading && quiz.error && <ErrorBanner error={quiz.error} title="Quiz results unavailable" />}
          {!quiz.loading && !quiz.error && (
            <DataTable
              columns={quizColumns}
              rows={quizRows}
              rowKey={(q) => q.id}
              empty={<EmptyState icon="🧠" title="No quizzes attempted yet" hint="Graded server-side — results appear right after you submit." />}
            />
          )}
        </section>
      </div>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        <h2 className="mb-4 text-sm font-semibold text-slate-200">Published AR lessons</h2>
        {contents.loading && <Spinner label="Loading AR content…" />}
        {!contents.loading && contents.error && <ErrorBanner error={contents.error} title="AR content unavailable" />}
        {!contents.loading && !contents.error && (
          <DataTable
            columns={[
              { key: 't', header: 'Lesson', render: (c: ContentSummary) => <span className="font-medium text-slate-200">{c.title}</span> },
              { key: 's', header: 'Subject', render: (c: ContentSummary) => c.subject },
              { key: 'g', header: 'Grade', render: (c: ContentSummary) => c.grade ?? '—' },
              {
                key: 'go',
                header: '',
                align: 'right',
                render: (c: ContentSummary) => (
                  <Link
                    to={`/ar/${c.id}`}
                    className="rounded-lg border border-cyan-500/40 px-2.5 py-1 text-xs font-medium text-cyan-300 transition hover:bg-cyan-500/10"
                  >
                    Open AR
                  </Link>
                ),
              },
            ]}
            rows={contents.data ?? []}
            rowKey={(c) => c.id}
            empty={<EmptyState icon="🧊" title="No published lessons" hint="Developers publish AR lessons; they show up here." />}
          />
        )}
      </section>
    </div>
  )
}
