import type { AuthResponse, RegisterPayload, Role, User } from './types'

/**
 * Minimal fetch wrapper for the AR EduVision API.
 * - relative base ('') so the Vite dev-server proxy (/api -> :8081) is used
 * - Bearer token from localStorage (`eduvision.token`)
 * - JSON content-type only when a body is sent
 * - 401 -> session cleared + redirect to /login
 * - !ok -> throws an object shaped { status, message } using the server's `message`
 */

export const TOKEN_KEY = 'eduvision.token'
export const USER_KEY = 'eduvision.user'

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function storeSession(token: string, user: User): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** Role -> dashboard landing route. */
export function dashboardPath(role: Role): string {
  switch (role) {
    case 'TEACHER':
      return '/teacher'
    case 'STUDENT':
      return '/student'
    case 'DEVELOPER':
      return '/developer'
  }
}

async function request<T>(path: string, opts: RequestInit = {}, redirectOn401 = true): Promise<T> {
  const headers = new Headers(opts.headers as HeadersInit | undefined)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (opts.body) headers.set('Content-Type', 'application/json')

  const res = await fetch(path, { ...opts, headers })

  if (res.status === 401 && redirectOn401) {
    clearSession()
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
    throw new ApiError(401, 'Session expired — please sign in again.')
  }

  const text = await res.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }

  if (!res.ok) {
    const message =
      body && typeof body === 'object' && 'message' in body && typeof (body as { message: unknown }).message === 'string'
        ? ((body as { message: string }).message as string)
        : `Request failed (HTTP ${res.status})`
    throw new ApiError(res.status, message)
  }

  return body as T
}

/** Authenticated JSON request. Throws ApiError {status, message} on failure. */
export function apiFetch<T>(path: string, opts: RequestInit = {}): Promise<T> {
  return request<T>(path, opts, true)
}

/** POST /api/auth/login -> stores token + user, returns the auth payload. */
export async function login(email: string, password: string): Promise<AuthResponse> {
  const res = await request<AuthResponse>(
    '/api/auth/login',
    { method: 'POST', body: JSON.stringify({ email, password }) },
    false, // a bad password must show an inline error, not redirect
  )
  storeSession(res.token, res.user)
  return res
}

/** POST /api/auth/register -> stores token + user (server returns a full AuthResponse). */
export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const res = await request<AuthResponse>(
    '/api/auth/register',
    { method: 'POST', body: JSON.stringify(payload) },
    false,
  )
  storeSession(res.token, res.user)
  return res
}

export function logout(): void {
  clearSession()
}
