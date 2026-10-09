import { useApi } from '../api/useApi'
import type { ClassDto, ContentSummary, HealthPayload, SeedSummary } from '../api/types'
import StatCard from '../components/StatCard'
import DataTable, { type Column } from '../components/DataTable'
import ErrorBanner from '../components/ErrorBanner'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'

function statusPill(status: string) {
  const published = status === 'PUBLISHED'
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ring-1 ring-inset ${
        published ? 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30' : 'bg-amber-500/15 text-amber-300 ring-amber-500/30'
      }`}
    >
      {status}
    </span>
  )
}

/** /developer — system / seed info straight from the backend. */
export default function DeveloperDashboard() {
  const seed = useApi<SeedSummary>('/api/dev/seed-summary')
  const health = useApi<HealthPayload>('/api/health')
  const classes = useApi<ClassDto[]>('/api/classes')
  const contents = useApi<ContentSummary[]>('/api/ar-content')

  const s = seed.data
  const contentRows = contents.data ?? []

  const contentColumns: Column<ContentSummary>[] = [
    {
      key: 'title',
      header: 'Title',
      render: (c) => (
        <div>
          <p className="font-medium text-slate-200">{c.title}</p>
          <p className="text-xs text-slate-500">{c.markerId ? `marker: ${c.markerId}` : 'no marker'}</p>
        </div>
      ),
    },
    { key: 'subject', header: 'Subject', render: (c) => c.subject },
    { key: 'grade', header: 'Grade', render: (c) => c.grade ?? '—' },
    { key: 'status', header: 'Status', render: (c) => statusPill(c.status) },
    { key: 'ver', header: 'v', align: 'right', render: (c) => c.version },
    { key: 'parts', header: 'Parts', align: 'right', render: (c) => c.partCount },
    { key: 'q', header: 'Questions', align: 'right', render: (c) => c.questionCount },
    { key: 'by', header: 'Author', render: (c) => <span className="text-slate-400">{c.createdBy ?? '—'}</span> },
  ]

  const classColumns: Column<ClassDto>[] = [
    { key: 'name', header: 'Class', render: (c) => <span className="font-medium text-slate-200">{c.name}</span> },
    { key: 'grade', header: 'Grade', render: (c) => c.grade ?? '—' },
    { key: 'students', header: 'Students', align: 'right', render: (c) => c.studentCount },
    { key: 'a', header: 'Assignments', align: 'right', render: (c) => c.assignmentCount },
  ]

  const seedCards: { key: keyof SeedSummary; label: string; accent: 'cyan' | 'violet' | 'emerald' | 'amber' | 'rose' }[] = [
    { key: 'users', label: 'Users', accent: 'cyan' },
    { key: 'classes', label: 'Classes', accent: 'violet' },
    { key: 'contents', label: 'AR contents', accent: 'emerald' },
    { key: 'questions', label: 'Quiz questions', accent: 'amber' },
    { key: 'assignments', label: 'Assignments', accent: 'rose' },
    { key: 'classMembers', label: 'Enrolments', accent: 'cyan' },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">System overview</h1>
          <p className="text-sm text-slate-500">Seed data and service health for this demo stack.</p>
        </div>
        <span
          className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium ring-1 ring-inset ${
            health.data?.status === 'ok'
              ? 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/30'
              : health.loading
                ? 'bg-slate-500/10 text-slate-300 ring-slate-500/30'
                : 'bg-rose-500/10 text-rose-300 ring-rose-500/30'
          }`}
        >
          <span className="h-1.5 w-1.5 rounded-full bg-current" />
          API {health.loading ? 'checking…' : health.data?.status === 'ok' ? 'healthy' : 'unreachable'}
        </span>
      </div>

      {health.error && <ErrorBanner error={health.error} title="GET /api/health failed" />}
      {seed.error && <ErrorBanner error={seed.error} title="GET /api/dev/seed-summary failed" />}

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-6">
        {seedCards.map((card) => (
          <StatCard
            key={card.key}
            label={card.label}
            value={seed.loading ? '' : s ? s[card.key] : '—'}
            loading={seed.loading}
            accent={card.accent}
            hint={`/api/dev/seed-summary`}
          />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5 xl:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-200">AR content inventory</h2>
            <span className="text-xs text-slate-500">GET /api/ar-content</span>
          </div>
          {contents.loading && <Spinner label="Loading content…" />}
          {!contents.loading && contents.error && <ErrorBanner error={contents.error} title="AR content unavailable" />}
          {!contents.loading && !contents.error && (
            <DataTable
              columns={contentColumns}
              rows={contentRows}
              rowKey={(c) => c.id}
              empty={<EmptyState icon="🧩" title="No AR content" hint="POST /api/ar-content creates the first draft." />}
            />
          )}
        </section>

        <section className="space-y-6">
          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-slate-200">Classes</h2>
              <span className="text-xs text-slate-500">GET /api/classes</span>
            </div>
            {classes.loading && <Spinner label="Loading…" />}
            {!classes.loading && classes.error && <ErrorBanner error={classes.error} title="Classes unavailable" />}
            {!classes.loading && !classes.error && (
              <DataTable
                columns={classColumns}
                rows={classes.data ?? []}
                rowKey={(c) => c.id}
                empty={
                  <EmptyState
                    icon="🏫"
                    title="No classes visible"
                    hint="A developer account sees only classes it belongs to — the seeded class belongs to the teacher."
                  />
                }
              />
            )}
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
            <h2 className="mb-4 text-sm font-semibold text-slate-200">Database rows</h2>
            {seed.loading && <Spinner label="Loading…" />}
            {!seed.loading && !seed.error && s && (
              <dl className="divide-y divide-slate-800/70 text-sm">
                {[
                  ['parts', s.parts],
                  ['questionBanks', s.questions],
                  ['users', s.users],
                  ['contents', s.contents],
                ].map(([label, value]) => (
                  <div key={String(label)} className="flex items-center justify-between py-2.5">
                    <dt className="text-slate-400">{label}</dt>
                    <dd className="tabular-nums text-slate-200">{value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
