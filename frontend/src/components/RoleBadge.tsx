import type { Role } from '../api/types'

const STYLES: Record<Role, string> = {
  TEACHER: 'bg-violet-500/15 text-violet-300 ring-violet-500/30',
  STUDENT: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  DEVELOPER: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
}

export default function RoleBadge({ role, className = '' }: { role: Role; className?: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider ring-1 ring-inset ${
        STYLES[role] ?? 'bg-slate-500/15 text-slate-300 ring-slate-500/30'
      } ${className}`}
    >
      {role}
    </span>
  )
}
