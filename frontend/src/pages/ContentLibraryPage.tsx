import { Link } from 'react-router-dom'
import { useApi } from '../api/useApi'
import { getStoredUser } from '../api/client'
import type { ContentSummary } from '../api/types'
import DataTable, { type Column } from '../components/DataTable'
import ErrorBanner from '../components/ErrorBanner'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'

const TITLES: Record<string, string> = {
  TEACHER: 'AR content library',
  STUDENT: 'AR lessons',
  DEVELOPER: 'Content studio',
}

/** Shared AR catalogue page used at /teacher/content, /student/lessons and /developer/content. */
export default function ContentLibraryPage() {
  const user = getStoredUser()
  const contents = useApi<ContentSummary[]>('/api/ar-content')
  const rows = contents.data ?? []

  const columns: Column<ContentSummary>[] = [
    {
      key: 'title',
      header: 'Lesson',
      render: (c) => (
        <div>
          <p className="font-medium text-slate-200">{c.title}</p>
          <p className="text-xs text-slate-500">{c.description ? c.description.slice(0, 90) : 'No description'}</p>
        </div>
      ),
    },
    { key: 'subject', header: 'Subject', render: (c) => c.subject },
    { key: 'grade', header: 'Grade', render: (c) => c.grade ?? '—' },
    {
      key: 'status',
      header: 'Status',
      render: (c) => (
        <span
          className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ring-1 ring-inset ${
            c.status === 'PUBLISHED'
              ? 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30'
              : 'bg-amber-500/15 text-amber-300 ring-amber-500/30'
          }`}
        >
          {c.status}
        </span>
      ),
    },
    { key: 'marker', header: 'Marker', render: (c) => <span className="font-mono text-xs text-slate-400">{c.markerId ?? '—'}</span> },
    { key: 'parts', header: 'Parts', align: 'right', render: (c) => c.partCount },
    { key: 'q', header: 'Questions', align: 'right', render: (c) => c.questionCount },
    {
      key: 'open',
      header: '',
      align: 'right',
      render: (c) => (
        <Link
          to={`/ar/${c.id}`}
          className="rounded-lg border border-cyan-500/40 px-2.5 py-1 text-xs font-medium text-cyan-300 transition hover:bg-cyan-500/10"
        >
          Open AR
        </Link>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            {user ? (TITLES[user.role] ?? 'AR content') : 'AR content'}
          </h1>
          <p className="text-sm text-slate-500">GET /api/ar-content — filtered by the server for your role.</p>
        </div>
        <span className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-400">
          {rows.length} lesson{rows.length === 1 ? '' : 's'}
        </span>
      </div>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5">
        {contents.loading && <Spinner label="Loading AR content…" />}
        {!contents.loading && contents.error && <ErrorBanner error={contents.error} title="AR content unavailable" />}
        {!contents.loading && !contents.error && (
          <DataTable
            columns={columns}
            rows={rows}
            rowKey={(c) => c.id}
            empty={
              <EmptyState
                icon="🧊"
                title="No AR content yet"
                hint="Developers create lessons with POST /api/ar-content, then publish them for students."
              />
            }
          />
        )}
      </section>
    </div>
  )
}
