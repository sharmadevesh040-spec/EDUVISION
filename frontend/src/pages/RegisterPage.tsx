import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { dashboardPath, getStoredUser, register } from '../api/client'
import type { Role } from '../api/types'

const inputClass =
  'w-full rounded-xl border border-slate-700 bg-slate-900 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-600 outline-none transition focus:border-cyan-500/60 focus:ring-2 focus:ring-cyan-500/20'

const ROLES: { value: Role; label: string }[] = [
  { value: 'STUDENT', label: 'Student' },
  { value: 'TEACHER', label: 'Teacher' },
  { value: 'DEVELOPER', label: 'Developer' },
]

export default function RegisterPage() {
  const navigate = useNavigate()
  const existing = getStoredUser()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('STUDENT')
  const [grade, setGrade] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (existing) return <Navigate to={dashboardPath(existing.role)} replace />

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!name.trim() || !email.trim() || !password) {
      setError('Name, email and password are required.')
      return
    }
    if (role === 'TEACHER' && !grade.trim()) {
      setError('Teachers must provide a grade/board.')
      return
    }
    setBusy(true)
    try {
      const res = await register({
        name: name.trim(),
        email: email.trim(),
        password,
        role,
        ...(role === 'TEACHER' ? { grade: grade.trim() } : {}),
      })
      navigate(dashboardPath(res.user.role), { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed.')
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-950 px-5 py-10 text-slate-100">
      <div className="w-full max-w-md">
        <Link to="/login" className="mb-8 flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-400 to-blue-600 font-black text-slate-950">
            AR
          </span>
          <span className="text-lg font-semibold tracking-tight">
            AR <span className="text-cyan-400">EduVision</span>
          </span>
        </Link>

        <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
        <p className="mt-1 text-sm text-slate-500">
          Accounts are stored server-side with PBKDF2-hashed passwords.
        </p>

        <form className="mt-7 space-y-4" onSubmit={(e) => void submit(e)}>
          <div>
            <label htmlFor="name" className="mb-1.5 block text-xs font-medium text-slate-400">
              Full name
            </label>
            <input id="name" className={inputClass} placeholder="Ada Lovelace" value={name} onChange={(e) => setName(e.target.value)} />
          </div>

          <div>
            <label htmlFor="reg-email" className="mb-1.5 block text-xs font-medium text-slate-400">
              Email
            </label>
            <input
              id="reg-email"
              type="email"
              className={inputClass}
              placeholder="you@eduvision.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div>
            <label htmlFor="reg-password" className="mb-1.5 block text-xs font-medium text-slate-400">
              Password <span className="text-slate-600">(min 8 characters)</span>
            </label>
            <input
              id="reg-password"
              type="password"
              className={inputClass}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <div>
            <label htmlFor="role" className="mb-1.5 block text-xs font-medium text-slate-400">
              Role
            </label>
            <select id="role" className={inputClass} value={role} onChange={(e) => setRole(e.target.value as Role)}>
              {ROLES.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          </div>

          {role === 'TEACHER' && (
            <div>
              <label htmlFor="grade" className="mb-1.5 block text-xs font-medium text-slate-400">
                Grade / board <span className="text-rose-400">(required for teachers)</span>
              </label>
              <input
                id="grade"
                className={inputClass}
                placeholder="e.g. 10"
                value={grade}
                onChange={(e) => setGrade(e.target.value)}
              />
            </div>
          )}

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
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already registered?{' '}
          <Link to="/login" className="font-medium text-cyan-400 hover:text-cyan-300">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
