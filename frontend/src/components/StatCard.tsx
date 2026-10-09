import type { ReactNode } from 'react'

/**
 * KPI tile. `value` is always supplied by the caller from a real API response —
 * never a hard-coded number.
 */
export default function StatCard({
  label,
  value,
  hint,
  accent = 'cyan',
  loading = false,
}: {
  label: string
  value: ReactNode
  hint?: string
  accent?: 'cyan' | 'violet' | 'emerald' | 'amber' | 'rose'
  loading?: boolean
}) {
  const accents: Record<string, string> = {
    cyan: 'text-cyan-300',
    violet: 'text-violet-300',
    emerald: 'text-emerald-300',
    amber: 'text-amber-300',
    rose: 'text-rose-300',
  }
  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-5 shadow-sm transition hover:border-slate-700">
      <p className="text-[11px] font-medium uppercase tracking-widest text-slate-500">{label}</p>
      <p className={`mt-2 text-3xl font-semibold tabular-nums ${accents[accent] ?? accents.cyan}`}>
        {loading ? <span className="inline-block h-8 w-16 animate-pulse rounded bg-slate-800" /> : value}
      </p>
      {hint && !loading && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}
