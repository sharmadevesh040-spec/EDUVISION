import type { ReactNode } from 'react'

/** Friendly zero-data state so an empty API response still looks intentional. */
export default function EmptyState({
  icon = '📭',
  title,
  hint,
  action,
}: {
  icon?: string
  title: string
  hint?: string
  action?: ReactNode
}) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-700 bg-slate-900/40 px-6 py-10 text-center">
      <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-slate-800 text-xl">
        {icon}
      </div>
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {hint && <p className="mx-auto mt-1 max-w-md text-xs text-slate-500">{hint}</p>}
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  )
}
