import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { dashboardPath, getStoredUser, getToken } from './api/client'
import type { Role } from './api/types'
import AppShell from './components/AppShell'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import TeacherDashboard from './pages/TeacherDashboard'
import TeacherAnalyticsPage from './pages/TeacherAnalyticsPage'
import StudentDashboard from './pages/StudentDashboard'
import DeveloperDashboard from './pages/DeveloperDashboard'
import ContentLibraryPage from './pages/ContentLibraryPage'
import ArViewerPage from './pages/ArViewerPage'
import QuizPage from './pages/QuizPage'
import AiTutorPage from './pages/AiTutorPage'
import TeacherLivePage from './pages/TeacherLivePage'
import StudentLivePage from './pages/StudentLivePage'

/**
 * Route guard:
 * - no token            -> /login
 * - wrong role          -> the caller's own dashboard
 * - otherwise           -> render the app shell around the page
 */
function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const token = getToken()
  const user = getStoredUser()

  if (!token || !user) return <Navigate to="/login" replace />
  if (role && user.role !== role) return <Navigate to={dashboardPath(user.role)} replace />
  return <AppShell>{children}</AppShell>
}

function HomeRedirect() {
  const token = getToken()
  const user = getStoredUser()
  if (!token || !user) return <Navigate to="/login" replace />
  return <Navigate to={dashboardPath(user.role)} replace />
}

function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-950 px-6 text-center text-slate-100">
      <p className="text-5xl font-semibold text-cyan-400">404</p>
      <p className="text-sm text-slate-400">That page does not exist.</p>
      <Link
        to="/"
        className="rounded-xl border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800"
      >
        Go to my dashboard
      </Link>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<HomeRedirect />} />

        {/* Teacher */}
        <Route
          path="/teacher"
          element={
            <RequireAuth role="TEACHER">
              <TeacherDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="/teacher/analytics"
          element={
            <RequireAuth role="TEACHER">
              <TeacherAnalyticsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/teacher/content"
          element={
            <RequireAuth role="TEACHER">
              <ContentLibraryPage />
            </RequireAuth>
          }
        />
        <Route
          path="/teacher/ai-tutor"
          element={
            <RequireAuth role="TEACHER">
              <AiTutorPage />
            </RequireAuth>
          }
        />
        <Route
          path="/teacher/live"
          element={
            <RequireAuth role="TEACHER">
              <TeacherLivePage />
            </RequireAuth>
          }
        />

        {/* Student */}
        <Route
          path="/student"
          element={
            <RequireAuth role="STUDENT">
              <StudentDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="/student/lessons"
          element={
            <RequireAuth role="STUDENT">
              <ContentLibraryPage />
            </RequireAuth>
          }
        />
        <Route
          path="/student/ai-tutor"
          element={
            <RequireAuth role="STUDENT">
              <AiTutorPage />
            </RequireAuth>
          }
        />
        <Route
          path="/student/live"
          element={
            <RequireAuth role="STUDENT">
              <StudentLivePage />
            </RequireAuth>
          }
        />

        {/* Developer */}
        <Route
          path="/developer"
          element={
            <RequireAuth role="DEVELOPER">
              <DeveloperDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="/developer/content"
          element={
            <RequireAuth role="DEVELOPER">
              <ContentLibraryPage />
            </RequireAuth>
          }
        />

        {/* Shared pages (any signed-in role) */}
        <Route
          path="/ar/:contentId"
          element={
            <RequireAuth>
              <ArViewerPage />
            </RequireAuth>
          }
        />
        {/* Alias kept for deep links: same AR viewer page. */}
        <Route
          path="/viewer/:contentId"
          element={
            <RequireAuth>
              <ArViewerPage />
            </RequireAuth>
          }
        />
        <Route
          path="/quiz/:assignmentId"
          element={
            <RequireAuth>
              <QuizPage />
            </RequireAuth>
          }
        />

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  )
}
