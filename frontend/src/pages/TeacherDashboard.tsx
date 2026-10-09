import { useEffect, useState } from 'react'
import { useApi } from '../api/useApi'
import type {
  AnalyticsOverview,
  AssignmentDto,
  ClassDto,
  ContentSummary,
  RosterRow,
} from '../api/types'
import StatCard from '../components/StatCard'
import DataTable, { type Column } from '../components/DataTable'
import ErrorBanner from '../components/ErrorBanner'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'
import { avg, fmtDuration, fmtRelative } from '../utils/format'

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

/** /teacher — class roster + quick stats, all from the live API. */
export default function TeacherDashboard() {
  const classes = useApi<ClassDto[]>('/api/classes')
  const overview = useApi<AnalyticsOverview>('/api/analytics/overview')
  const assignments = useApi<AssignmentDto[]>('/api/assignments')
  const contents = useApi<ContentSummary[]>('/api/ar-content')

  const firstClassId = classes.data && classes.data.length > 0 ? classes.data[0].id : null
  const [selectedId, setSelectedId] = useState<number | null>(null)
  useEffect(() => {
    if (selectedId === null && firstClassId !== null) setSelectedId(firstClassId)
  }, [firstClassId, selectedId])

  const roster = useApi<RosterRow[]>(
    selectedId !== null ? `/api/classes/${selectedId}/roster` : null,
  )

  const classRows = classes.data ?? []
  const studentTotal = classRows.reduce((sum, c) => sum + c.studentCount, 0)
  const engagement =
    overview.data && overview.data.classes.length > 0
      ? avg(overview.data.classes.map((c) => c.engagementPercent))
      : null

  const classColumns: Column<ClassDto>[] = [
    { key: 'name', header: 'Class', render: (c) => <span className="font-medium text-slate-200">{c.name}</span> },
    { key: 'grade', header: 'Grade', render: (c) => c.grade ?? '—' },
    { key: 'section', header: 'Section', render: (c) => c.section ?? '—' },
    { key: 'students', header: 'Students', align: 'right', render: (c) => c.studentCount },
    { key: 'assignments', header: 'Assignments', align: 'right', render: (c) => c.assignmentCount },
    {
      key: 'teacher',
      header: 'Teacher',
      render: (c) => <span className="text-slate-400">{c.teacherName ?? '—'}</span>,
    },
  ]

  const rosterColumns: Column<RosterRow>[] = [
    {
      key: 'name',
      header: 'Student',
      render: (r) => (
        <div>
          <p className="font-medium text-slate-200">{r.name}</p>
          <p className="text-xs text-slate-500">{r.email}</p>
        </div>
      ),
    },
    {
      key: 'done',
      header: 'Assignments',
      align: 'right',
      render: (r) => (
        <span className="tabular-nums">
          {r.completedAssignments}/{r.totalAssignments}
        </span>
      ),
    },
    { key: 'quiz', header: 'Avg quiz', align: 'right', render: (r) => `${r.avgQuizScore}%` },
    { key: 'time', header: 'Time on task', align: 'right', render: (r) => fmtDuration(r.timeSpentSeconds) },
    { key: 'last', header: 'Last seen', render: (r) => <span className="text-slate-400">{fmtRelative(r.lastAccessed)}</span> },
  ]

  const assignmentColumns: Column<AssignmentDto>[] = [
    { key: 'title', header: 'Assignment', render: (a) => <span className="font-medium text-slate-200">{a.title}</span> },
    { key: 'class', header: 'Class', render: (a) => a.className },
    { key: 'content', header: 'AR lesson', render: (a) => <span className="text-slate-400">{a.contentTitle}</span> },
    { key: 'deadline', header: 'Deadline', render: (a) => fmtRelative(a.deadline) },
    { key: 'status', header: 'Status', render: (a) => statusPill(a.status) },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Teacher dashboard</h1>
          <p className="text-sm text-slate-500">
            {overview.data ? `Welcome back, ${overview.data.teacher}.` : 'Your classes, roster and AR assignments.'}
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            classes.reload()
            overview.reload()
            assignments.reload()
            contents.reload()
            roster.reload()
          }}
          className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800"
        >
          ↻ Refresh
        </button>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Classes" value={classes.loading ? '' : classRows.length} loading={classes.loading} hint="GET /api/classes" />
        <StatCard
          label="Students"
          value={classes.loading ? '' : studentTotal}
          loading={classes.loading}
          accent="violet"
          hint="Sum of studentCount across classes"
        />
        <StatCard
          label="Assignments"
          value={assignments.loading ? '' : (assignments.data ?? []).length}
          loading={assignments.loading}
          accent="emerald"
          hint="GET /api/assignments"
        />
        <StatCard
          label="Engagement"
          value={overview.loading ? '' : engagement === null ? '—' : `${engagement}%`}
          loading={overview.loading}
          accent="amber"
          hint={engagement === null ? 'analytics unavailable' : 'GET /api/analytics/overview'}
        />
      </div>

      {/* Roster */}
      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-slate-200">Class roster</h2>
            <p className="text-xs text-slate-500">Per-student activity for the selected class.</p>
          </div>
          {classRows.length > 0 && (
            <select
              className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-xs text-slate-200 outline-none focus:border-cyan-500/60"
              value={selectedId ?? ''}
              onChange={(e) => setSelectedId(Number(e.target.value))}
            >
              {classRows.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {classes.loading && <Spinner label="Loading classes…" />}
        {!classes.loading && classes.error && <ErrorBanner error={classes.error} title="Classes unavailable" />}
        {!classes.loading && !classes.error && classRows.length === 0 && (
          <EmptyState
            icon="🏫"
            title="No classes yet"
            hint="Create a class from the API (POST /api/classes) and students will appear here."
          />
        )}
        {!classes.loading && !classes.error && classRows.length > 0 && (
          <>
            {roster.loading && <Spinner label="Loading roster…" />}
            {!roster.loading && roster.error && <ErrorBanner error={roster.error} title="Roster unavailable" />}
            {!roster.loading && !roster.error && roster.data && roster.data.length === 0 && (
              <EmptyState
                icon="🧑‍🎓"
                title="No students enrolled"
                hint="Add students to this class with POST /api/classes/{id}/members."
              />
            )}
            {!roster.loading && roster.data && roster.data.length > 0 && (
              <DataTable columns={rosterColumns} rows={roster.data} rowKey={(r) => r.studentId} />
            )}
          </>
        )}
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        {/* Classes */}
        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-200">My classes</h2>
          {classes.loading && <Spinner label="Loading…" />}
          {!classes.loading && classes.error && <ErrorBanner error={classes.error} title="Classes unavailable" />}
          {!classes.loading && !classes.error && (
            <DataTable
              columns={classColumns}
              rows={classRows}
              rowKey={(c) => c.id}
              empty={<EmptyState icon="🏫" title="No classes yet" hint="Your class list will appear here." />}
            />
          )}
        </section>

        {/* Assignments */}
        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-200">Assignments I set</h2>
          {assignments.loading && <Spinner label="Loading…" />}
          {!assignments.loading && assignments.error && (
            <ErrorBanner error={assignments.error} title="Assignments unavailable" />
          )}
          {!assignments.loading && !assignments.error && (
            <DataTable
              columns={assignmentColumns}
              rows={assignments.data ?? []}
              rowKey={(a) => a.id}
              empty={
                <EmptyState
                  icon="📋"
                  title="No assignments yet"
                  hint="POST /api/assignments links an AR lesson to a class with a deadline."
                />
              }
            />
          )}
        </section>
      </div>

      {/* AR library */}
      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        <h2 className="mb-4 text-sm font-semibold text-slate-200">AR content library</h2>
        {contents.loading && <Spinner label="Loading AR content…" />}
        {!contents.loading && contents.error && <ErrorBanner error={contents.error} title="AR content unavailable" />}
        {!contents.loading && !contents.error && (
          <DataTable
            columns={[
              { key: 't', header: 'Title', render: (c: ContentSummary) => <span className="font-medium text-slate-200">{c.title}</span> },
              { key: 's', header: 'Subject', render: (c: ContentSummary) => c.subject },
              { key: 'g', header: 'Grade', render: (c: ContentSummary) => c.grade ?? '—' },
              { key: 'q', header: 'Questions', align: 'right', render: (c: ContentSummary) => c.questionCount },
              { key: 'p', header: 'Parts', align: 'right', render: (c: ContentSummary) => c.partCount },
            ]}
            rows={contents.data ?? []}
            rowKey={(c) => c.id}
            empty={<EmptyState icon="🧊" title="No AR content yet" hint="Developers publish lessons that show up here." />}
          />
        )}
      </section>
    </div>
  )
}
