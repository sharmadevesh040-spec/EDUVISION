import { useEffect, useState } from 'react'
import { useApi } from '../api/useApi'
import type { AnalyticsOverview } from '../api/types'
import StatCard from '../components/StatCard'
import DataTable, { type Column } from '../components/DataTable'
import ErrorBanner from '../components/ErrorBanner'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'
import SimpleBarChart from '../components/SimpleBarChart'
import { avg } from '../utils/format'
import type { OverviewClass } from '../api/types'

interface ClassAnalytics {
  classId: number
  className: string
  grade: string | null
  students: number
  assignments: number
  engagementPercent: number
  avgCompletionPercent: number
  avgQuizScore: number
  totalTimeSpentSeconds: number
  sessionsStarted: number
  sessionsCompleted: number
  completionRate: number
  topics: string[]
  lowActivityStudents: Array<{
    studentId: number
    name: string
    completionPercent: number
    lastAccessed: string | null
    reasons: string
    note: string
  }>
}

/** /teacher/analytics — overview table + bar charts + per-class detail (live API). */
export default function TeacherAnalyticsPage() {
  const overview = useApi<AnalyticsOverview>('/api/analytics/overview')
  const rows = overview.data?.classes ?? []

  const [selectedId, setSelectedId] = useState<number | null>(null)
  useEffect(() => {
    if (selectedId === null && rows.length > 0) setSelectedId(rows[0].classId)
  }, [rows, selectedId])

  const detail = useApi<ClassAnalytics | null>(selectedId !== null ? `/api/analytics/class/${selectedId}` : null)

  const columns: Column<OverviewClass>[] = [
    { key: 'name', header: 'Class', render: (c) => <span className="font-medium text-slate-200">{c.className}</span> },
    { key: 'students', header: 'Students', align: 'right', render: (c) => c.students },
    { key: 'assignments', header: 'Assignments', align: 'right', render: (c) => c.assignments },
    {
      key: 'engagement',
      header: 'Engagement',
      align: 'right',
      render: (c) => (
        <span className={c.engagementPercent >= 60 ? 'text-emerald-300' : 'text-amber-300'}>{c.engagementPercent}%</span>
      ),
    },
    { key: 'quiz', header: 'Avg quiz', align: 'right', render: (c) => `${c.avgQuizScore}%` },
  ]

  const engagement = rows.length > 0 ? avg(rows.map((c) => c.engagementPercent)) : null
  const quiz = rows.length > 0 ? avg(rows.map((c) => c.avgQuizScore)) : null
  const d = detail.data

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Class analytics</h1>
        <p className="text-sm text-slate-500">
          Engagement indicators that support your teaching — never automated judgements about students.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Classes" value={overview.loading ? '' : rows.length} loading={overview.loading} hint="GET /api/analytics/overview" />
        <StatCard
          label="Students"
          value={overview.loading ? '' : rows.reduce((s, c) => s + c.students, 0)}
          loading={overview.loading}
          accent="violet"
        />
        <StatCard label="Avg engagement" value={overview.loading ? '' : engagement === null ? '—' : `${engagement}%`} loading={overview.loading} accent="amber" />
        <StatCard label="Avg quiz score" value={overview.loading ? '' : quiz === null ? '—' : `${quiz}%`} loading={overview.loading} accent="emerald" />
      </div>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        <h2 className="mb-4 text-sm font-semibold text-slate-200">Engagement by class</h2>
        {overview.loading && <Spinner label="Loading analytics…" />}
        {!overview.loading && overview.error && (
          <ErrorBanner error={overview.error} title="Analytics unavailable" />
        )}
        {!overview.loading && !overview.error && (
          <DataTable
            columns={columns}
            rows={rows}
            rowKey={(c) => c.classId}
            empty={<EmptyState icon="📈" title="No classes to analyse yet" hint="Create a class and assign lessons to see engagement here." />}
          />
        )}
        {rows.length > 1 && (
          <div className="mt-4 rounded-xl border border-slate-800 bg-slate-950/60 p-4">
            <SimpleBarChart
              data={rows.map((c) => ({ label: c.className.split(' ')[0], value: c.engagementPercent }))}
              color="#fbbf24"
            />
          </div>
        )}
        {!overview.loading && !overview.error && overview.data && (
          <p className="mt-4 rounded-xl border border-slate-800 bg-slate-950/60 p-3 text-xs leading-relaxed text-slate-500">
            Server note: “{overview.data.teacher}” — engagement analytics support the teacher; signals are indicators,
            not automated high-stakes decisions about a student.
          </p>
        )}
      </section>

      {rows.length > 0 && (
        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-slate-200">Class detail</h2>
            <select
              value={selectedId ?? ''}
              onChange={(e) => setSelectedId(Number(e.target.value))}
              className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-xs text-slate-200"
            >
              {rows.map((c) => (
                <option key={c.classId} value={c.classId}>
                  {c.className}
                </option>
              ))}
            </select>
          </div>

          {detail.loading && <Spinner label="Loading class detail…" />}
          {!detail.loading && detail.error && <ErrorBanner error={detail.error} title="Class detail unavailable" />}

          {!detail.loading && !detail.error && d && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <StatCard label="Avg completion" value={`${d.avgCompletionPercent}%`} accent="cyan" />
                <StatCard label="Avg quiz" value={`${d.avgQuizScore}%`} accent="emerald" />
                <StatCard label="Time on task" value={`${Math.round(d.totalTimeSpentSeconds / 60)} min`} accent="amber" />
                <StatCard label="Sessions" value={`${d.sessionsCompleted}/${d.sessionsStarted}`} accent="violet" />
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Engagement</p>
                  <SimpleBarChart data={[{ label: d.className.split(' ')[0], value: d.engagementPercent }]} color="#22d3ee" />
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Quiz performance</p>
                  <SimpleBarChart
                    data={[{ label: d.className.split(' ')[0], value: d.avgQuizScore }]}
                    color="#34d399"
                  />
                </div>
              </div>

              {d.lowActivityStudents.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Needs a gentle nudge ({d.lowActivityStudents.length})
                  </p>
                  <ul className="space-y-2">
                    {d.lowActivityStudents.map((s) => (
                      <li
                        key={s.studentId}
                        className="rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-sm"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-medium text-slate-200">{s.name}</span>
                          <span className="text-xs text-amber-300">{s.completionPercent}% completed</span>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">{s.reasons}</p>
                        <p className="mt-1 text-[10px] italic text-slate-500">{s.note}</p>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </section>
      )}
    </div>
  )
}