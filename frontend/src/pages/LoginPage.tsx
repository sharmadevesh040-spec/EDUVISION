import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { dashboardPath, getStoredUser, login } from '../api/client'

interface DemoAccount {
  label: string
  email: string
  password: string
  role: 'TEACHER' | 'STUDENT' | 'DEVELOPER'
  accent: string
}

const DEMO_ACCOUNTS: DemoAccount[] = [
  {
    label: 'Teacher',
    email: 'teacher@eduvision.com',
    password: 'Teacher@123',
    role: 'TEACHER',
    accent: 'border-violet-500/40 hover:border-violet-400 hover:bg-violet-500/10',
  },
  {
    label: 'Student',
    email: 'student1@eduvision.com',
    password: 'Student@123',
    role: 'STUDENT',
    accent: 'border-emerald-500/40 hover:border-emerald-400 hover:bg-emerald-500/10',
  },
  {
    label: 'Developer',
    email: 'developer@eduvision.com',
    password: 'Developer@123',
    role: 'DEVELOPER',
    accent: 'border-amber-500/40 hover:border-amber-400 hover:bg-amber-500/10',
  },
]

const inputClass =
  'w-full rounded-xl border border-slate-700 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-600 outline-none transition focus:border-cyan-500/60 focus:ring-2 focus:ring-cyan-500/20'

export default function LoginPage() {
  const navigate = useNavigate()
  const existing = getStoredUser()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (existing) return <Navigate to={dashboardPath(existing.role)} replace />

  async function submit(target?: DemoAccount) {
    const mail = target ? target.email : email.trim()
    const pass = target ? target.password : password
    setError(null)
    if (!mail || !pass) {
      setError('Email and password are required.')
      return
    }
    setBusy(true)
    try {
      const res = await login(mail, pass)
      navigate(dashboardPath(res.user.role), { replace: true })
    } catch (err) {
      const status = err && typeof err === 'object' && 'status' in err ? (err as { status: number }).status : 0
      if (status === 401 || status === 400) {
        setError(err instanceof Error ? err.message : 'Invalid credentials.')
      } else if (status === 405) {
        setError('Sign-in failed (HTTP 405 Method Not Allowed). The request to /api/auth/login was rejected.')
      } else if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Sign-in failed. Is the backend running on port 8081?')
      }
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 lg:grid lg:grid-cols-2">
      {/* Brand panel */}
      <section className="relative hidden overflow-hidden border-r border-slate-800 p-12 lg:flex lg:flex-col lg:justify-between">
        <div className="pointer-events-none absolute -left-24 -top-24 h-96 w-96 rounded-full bg-cyan-500/20 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -right-16 h-96 w-96 rounded-full bg-violet-600/20 blur-3xl" />
        <div className="relative">
          <Link to="/login" className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400 to-blue-600 text-lg font-black text-slate-950">
              AR
            </span>
            <span className="text-xl font-semibold tracking-tight">
              AR <span className="text-cyan-400">EduVision</span>
            </span>
          </Link>
        </div>

        <div className="relative max-w-md">
          <h1 className="text-4xl font-semibold leading-tight tracking-tight">
            Bring the classroom
            <br />
            <span className="bg-gradient-to-r from-cyan-300 to-violet-400 bg-clip-text text-transparent">
              into augmented reality.
            </span>
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-slate-400">
            Teachers assign AR lessons, students explore 3D models and quizzes, developers publish and
            measure content — all from one walk-forward demo stack.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-slate-300">
            {[
              'Marker-based AR lessons with <model-viewer>',
              'Server-graded quizzes — answers never leave the backend',
              'Engagement analytics that support, never label, students',
            ].map((line) => (
              <li key={line} className="flex items-start gap-3">
                <span className="mt-0.5 text-cyan-400">▹</span>
                {line}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-slate-600">React 19 · Vite · Tailwind 4 · Spring Boot + JWT</p>
      </section>

      {/* Form panel */}
      <section className="flex min-h-screen items-center justify-center px-5 py-10">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400 to-blue-600 font-black text-slate-950">
              AR
            </span>
            <h1 className="mt-4 text-2xl font-semibold tracking-tight">
              AR <span className="text-cyan-400">EduVision</span>
            </h1>
          </div>

          <h2 className="text-2xl font-semibold tracking-tight">Sign in</h2>
          <p className="mt-1 text-sm text-slate-500">Use your school account or a one-click demo below.</p>

          <form
            className="mt-7 space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              void submit()
            }}
          >
            <div>
              <label htmlFor="email" className="mb-1.5 block text-xs font-medium text-slate-400">
                Email
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                className={inputClass}
                placeholder="you@eduvision.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="password" className="mb-1.5 block text-xs font-medium text-slate-400">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                className={inputClass}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            {error && (
              <p className="rounded-xl border border-rose-500/40 bg-rose-500/10 px-3.5 py-2.5 text-sm text-rose-200">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="w-full rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:from-cyan-400 hover:to-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div className="mt-7">
            <p className="mb-3 text-[11px] font-semibold uppercase tracking-widest text-slate-600">
              One-click demo accounts
            </p>
            <div className="grid gap-2.5">
              {DEMO_ACCOUNTS.map((acct) => (
                <button
                  key={acct.email}
                  type="button"
                  disabled={busy}
                  onClick={() => void submit(acct)}
                  className={`flex items-center justify-between rounded-xl border bg-slate-900/70 px-4 py-3 text-left transition disabled:opacity-60 ${acct.accent}`}
                >
                  <span>
                    <span className="block text-sm font-medium text-slate-200">{acct.label}</span>
                    <span className="block text-xs text-slate-500">{acct.email}</span>
                  </span>
                  <span className="text-xs font-medium text-slate-500">Sign in →</span>
                </button>
              ))}
            </div>
          </div>

          <p className="mt-7 text-center text-sm text-slate-500">
            No account yet?{' '}
            <Link to="/register" className="font-medium text-cyan-400 hover:text-cyan-300">
              Create one
            </Link>
          </p>
        </div>
      </section>
    </div>
  )
}
