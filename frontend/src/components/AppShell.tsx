import { useState } from 'react'
import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { getStoredUser, logout } from '../api/client'
import RoleBadge from './RoleBadge'

interface NavItem {
  to: string
  label: string
  icon: string
  end?: boolean
}

/** Role-aware left navigation. */
function navFor(role: string): NavItem[] {
  switch (role) {
    case 'TEACHER':
      return [
        { to: '/teacher', label: 'Dashboard', icon: '📊', end: true },
        { to: '/teacher/analytics', label: 'Class analytics', icon: '📈' },
        { to: '/teacher/content', label: 'AR library', icon: '🧊' },
        { to: '/teacher/ai-tutor', label: 'AI Tutor', icon: '🎓' },
        { to: '/teacher/live', label: 'Live classroom', icon: '🛰️' },
      ]
    case 'STUDENT':
      return [
        { to: '/student', label: 'My dashboard', icon: '🎒', end: true },
        { to: '/student/lessons', label: 'AR lessons', icon: '🧊' },
        { to: '/student/ai-tutor', label: 'AI Tutor', icon: '🎓' },
        { to: '/student/live', label: 'Live classroom', icon: '🛰️' },
      ]
    case 'DEVELOPER':
      return [
        { to: '/developer', label: 'System overview', icon: '🖥️', end: true },
        { to: '/developer/content', label: 'Content studio', icon: '🧩' },
      ]
    default:
      return []
  }
}

/**
 * App shell: sticky top bar (brand + user + logout) and a responsive left sidebar
 * that collapses into a hamburger drawer below `md`.
 */
export default function AppShell({ children }: { children: ReactNode }) {
  const user = getStoredUser()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const items = user ? navFor(user.role) : []

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const sidebar = (
    <nav className="flex h-full flex-col gap-1 p-4">
      <div className="px-3 pb-3 text-[10px] font-semibold uppercase tracking-widest text-slate-600">
        {user ? `${user.role} workspace` : 'Workspace'}
      </div>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
              isActive
                ? 'bg-cyan-500/10 text-cyan-300 ring-1 ring-inset ring-cyan-500/20'
                : 'text-slate-400 hover:bg-slate-800/70 hover:text-slate-200'
            }`
          }
        >
          <span className="text-base leading-none">{item.icon}</span>
          {item.label}
        </NavLink>
      ))}

      <div className="mt-auto rounded-xl border border-slate-800 bg-slate-900/60 p-3 text-[11px] leading-relaxed text-slate-500">
        Signed in as <span className="text-slate-300">{user?.email}</span>
        <br />
        3D models stream live from the public web.
      </div>
    </nav>
  )

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Top bar */}
      <header className="sticky top-0 z-30 border-b border-slate-800 bg-slate-950/85 backdrop-blur">
        <div className="flex h-14 items-center gap-3 px-4">
          <button
            type="button"
            aria-label="Toggle navigation"
            onClick={() => setOpen((v) => !v)}
            className="rounded-lg border border-slate-700 p-2 text-slate-300 hover:bg-slate-800 md:hidden"
          >
            <span className="block h-0.5 w-4 bg-current" />
            <span className="mt-1 block h-0.5 w-4 bg-current" />
            <span className="mt-1 block h-0.5 w-4 bg-current" />
          </button>

          <div className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-cyan-400 to-blue-600 text-sm font-black text-slate-950">
              AR
            </span>
            <span className="text-sm font-semibold tracking-tight sm:text-base">
              AR <span className="text-cyan-400">EduVision</span>
            </span>
          </div>

          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium leading-tight text-slate-200">{user?.name ?? '—'}</p>
              <p className="text-[11px] leading-tight text-slate-500">{user?.grade ? `Grade ${user.grade}` : user?.email}</p>
            </div>
            {user && <RoleBadge role={user.role} />}
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-medium text-slate-300 transition hover:border-rose-500/50 hover:bg-rose-500/10 hover:text-rose-300"
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      <div className="flex">
        {/* Desktop sidebar */}
        <aside className="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-60 shrink-0 border-r border-slate-800 bg-slate-950 md:block">
          {sidebar}
        </aside>

        {/* Mobile drawer */}
        {open && (
          <div className="fixed inset-0 z-40 md:hidden">
            <div className="absolute inset-0 bg-black/60" onClick={() => setOpen(false)} aria-hidden />
            <aside className="absolute left-0 top-0 h-full w-64 border-r border-slate-800 bg-slate-950">
              <div className="flex h-14 items-center justify-between border-b border-slate-800 px-4">
                <span className="text-sm font-semibold">Menu</span>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  className="rounded-lg p-2 text-slate-400 hover:bg-slate-800"
                  aria-label="Close navigation"
                >
                  ✕
                </button>
              </div>
              <div className="h-[calc(100%-3.5rem)] overflow-y-auto">{sidebar}</div>
            </aside>
          </div>
        )}

        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  )
}
