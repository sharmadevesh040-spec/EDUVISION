// Vercel serverless catch-all: the FULL EduVision API for the deployed site.
//
// Route: /api/*   (file: api/[...all].js)
// Data:  api/seed.json — captured 1:1 from the real Java Spring Boot backend
//        (localhost:8081, eduvision-backend-1.0.0.jar), so every payload has
//        exactly the shape the React app expects (types.ts).
//
// Covered routes (everything the frontend calls):
//   GET  /api/health                      GET  /api/auth/me
//   POST /api/auth/login                  POST /api/auth/register
//   GET  /api/ar-content                  GET  /api/ar-content/{id}
//   GET  /api/ar-content/marker/{markerId}
//   GET  /api/classes                     GET  /api/classes/{id}/roster
//   GET  /api/classes/{id}/schedule        POST /api/classes/{id}/schedule (teacher)
//   PUT  /api/classes/{id}/schedule/{sid}  DELETE /api/classes/{id}/schedule/{sid}
//   GET  /api/schedule/day/{DAY}
//   GET  /api/assignments                 GET  /api/assignments/student
//   GET  /api/progress/me                 GET  /api/quiz/results/me
//   POST /api/quiz/submit                 (real server-side grading)
//   GET  /api/analytics/overview          GET  /api/analytics/class/{id}
//   GET  /api/dev/seed-summary            POST /api/ai/ask (grounded)
//   POST /api/live/sessions               GET  /api/live/sessions/{id}
//   POST /api/live/sessions/{code}/join   POST /api/live/sessions/{id}/heartbeat
//   POST /api/live/sessions/{id}/end      GET  /api/live/sessions
//
// Student token NEVER sees quiz answers: details vs detailsTeacher are split.
// Live-classroom state is per-instance (no DB on Vercel) — see LIVE notes.

const seed = require('./seed.json')

const USERS = {
  'student1@eduvision.com':  { id: 3, name: 'Ravi Sharma',       role: 'STUDENT',   grade: '10', pw: 'Student@123' },
  'teacher@eduvision.com':   { id: 1, name: 'Ms. Elena Fischer', role: 'TEACHER',   grade: '10', pw: 'Teacher@123' },
  'developer@eduvision.com': { id: 2, name: 'Dev Arjun Mehta',   role: 'DEVELOPER', grade: '',   pw: 'Developer@123' },
}

const ROLES = ['TEACHER', 'STUDENT', 'DEVELOPER']
const LIVE_TTL_MS = 30 * 60 * 1000 // a live session auto-expires after 30 min
const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// ---- per-instance state (warm lambda only; resets on cold start) -----------
const endedIds = new Set()
const joinedBySession = new Map()
const attemptsByKey = new Map()
const scheduleByClass = new Map() // classId -> [ ScheduleDto ]  (lazily seeded)
let scheduleNextId = null
let quizResultsMine = null // lazily cloned from seed.quizResults

function send(res, code, obj) {
  res.statusCode = code
  res.setHeader('Content-Type', 'application/json')
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization')
  res.end(JSON.stringify(obj))
}

function roleOf(req) {
  const h = req.headers || {}
  const auth = String(h.authorization || h.Authorization || '')
  const m = auth.match(/demo-token-([A-Za-z]+)/) || auth.match(/Bearer\s+([A-Za-z]+)/)
  const r = m ? m[1].toUpperCase() : ''
  return ROLES.indexOf(r) >= 0 ? r : 'STUDENT'
}

function userForRole(role) {
  const email = Object.keys(USERS).find((e) => USERS[e].role === role) || 'student1@eduvision.com'
  const u = USERS[email]
  return { id: u.id, name: u.name, email, role: u.role, grade: u.grade }
}

function parseBody(req) {
  try {
    if (req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body)) return req.body
    if (typeof req.body === 'string' && req.body) return JSON.parse(req.body)
    if (Buffer.isBuffer(req.body)) return JSON.parse(req.body.toString('utf8'))
    if (typeof req.rawBody === 'string' && req.rawBody) return JSON.parse(req.rawBody)
    if (Buffer.isBuffer(req.rawBody)) return JSON.parse(req.rawBody.toString('utf8'))
  } catch { /* fall through */ }
  return {}
}

/** Student-safe detail (correctOption/explanation stripped) unless teacher/dev. */
function detailFor(id, role) {
  const key = String(id)
  const full = seed.detailsTeacher && seed.detailsTeacher[key]
  if (role !== 'STUDENT' && full) return full
  return (seed.details && seed.details[key]) || full || null
}

function markerDetail(markerId, role) {
  const marker = String(markerId)
  if (role !== 'STUDENT' && seed.detailsTeacher) {
    for (const k of Object.keys(seed.detailsTeacher)) {
      if (String(seed.detailsTeacher[k].markerId) === marker) return seed.detailsTeacher[k]
    }
  }
  if (seed.markerDetails && seed.markerDetails[marker]) {
    // student-safe copy: strip answers if we have the teacher variant
    const safe = seed.markerDetails[marker]
    if (role !== 'STUDENT') return safe
    return stripAnswers(safe)
  }
  for (const k of Object.keys(seed.details || {})) {
    if (String(seed.details[k].markerId) === marker) return seed.details[k]
  }
  return null
}

function stripAnswers(detail) {
  if (!detail || roleIsSafe(detail)) return detail
  const copy = Object.assign({}, detail)
  copy.questions = (detail.questions || []).map((q) =>
    Object.assign({}, q, { correctOption: null, explanation: null })
  )
  return copy
}

function roleIsSafe(detail) {
  const q = (detail.questions || [])[0]
  return !q || q.correctOption === null || q.correctOption === undefined
}

// ---------------------------------------------------------------------------
// class schedule — mirrors ClassScheduleController (weekly timetable slots)
// ---------------------------------------------------------------------------
function seedSchedules() {
  if (scheduleNextId !== null) return
  const src = seed.classSchedules || {}
  Object.keys(src).forEach((k) => scheduleByClass.set(Number(k), (src[k] || []).slice()))
  let maxId = 0
  scheduleByClass.forEach((list) => list.forEach((s) => { if (s.id > maxId) maxId = s.id }))
  scheduleNextId = maxId + 1
}

function dayIndex(day) {
  const i = DAY_ORDER.indexOf(String(day || '').toUpperCase())
  return i >= 0 ? i : 99
}

function schedulesFor(classId) {
  seedSchedules()
  const list = scheduleByClass.get(Number(classId)) || []
  return list
    .slice()
    .sort((a, b) => dayIndex(a.dayOfWeek) - dayIndex(b.dayOfWeek) || String(a.startTime).localeCompare(String(b.startTime)))
}

function scheduleDto(classId, id, day, start, end, room, notes) {
  const cls = (seed.classes || [])[0] || {}
  return {
    id,
    classId: Number(classId),
    className: cls.name || 'Grade 10 Science - A',
    grade: cls.grade || '',
    section: cls.section || '',
    dayOfWeek: day,
    startTime: start,
    endTime: end,
    room: (room && String(room).trim()) || null,
    notes: (notes && String(notes).trim()) || null,
  }
}

/** Canonical HH:mm (drops :00 seconds) — mirrors java.time.LocalTime.toString(). */
function canonTime(t) {
  const m = String(t).match(/^(\d{2}):(\d{2})(?::(\d{2}))?$/)
  if (!m) return null
  return m[3] && m[3] !== '00' ? `${m[1]}:${m[2]}:${m[3]}` : `${m[1]}:${m[2]}`
}

/** Validates a ScheduleRequest body; returns {error} string or {day,start,end}. */
function validateSlot(b) {
  const day = String(b.dayOfWeek || '').toUpperCase()
  if (DAY_ORDER.indexOf(day) < 0) return { error: 'dayOfWeek must be one of ' + DAY_ORDER.join(', ') }
  const start = canonTime(b.startTime)
  const end = canonTime(b.endTime)
  if (!start) return { error: 'startTime must be a time in HH:mm format' }
  if (!end) return { error: 'endTime must be a time in HH:mm format' }
  if (end <= start) return { error: 'endTime must be after startTime' }
  return { day, start, end }
}

// ---------------------------------------------------------------------------
// quiz grading — mirrors QuizController.mySubmit
// ---------------------------------------------------------------------------
function gradeQuiz(role, token, b) {
  const detail = detailFor(b.contentId, role) || {}
  const teacher = seed.detailsTeacher && seed.detailsTeacher[String(b.contentId)]
  const questions = (teacher && teacher.questions) || detail.questions || []
  const byId = {}
  questions.forEach((q) => { byId[q.id] = q })

  const given = Array.isArray(b.answers) ? b.answers : []
  let score = 0
  const results = given.map((a) => {
    const q = byId[a.questionId]
    const correct = q ? String(q.correctOption || '').trim().toLowerCase() : ''
    const sel = String(a.selected || '').trim().toLowerCase()
    const ok = !!q && correct !== '' && (sel === correct || sel === correct.charAt(0))
    if (ok) score += 1
    return {
      questionId: a.questionId,
      selected: sel,
      correct: ok,
      correctOption: correct,
      explanation: q ? q.explanation : null,
      topic: q ? q.topic : null,
    }
  })

  const total = questions.length || results.length || 1
  const key = token + '|' + (b.assignmentId || 0)
  const attempts = (attemptsByKey.get(key) || 0) + 1
  const prevBest = Number(attemptsByKey.get(key + '|best') || 0)
  const bestScore = Math.max(score, prevBest)
  attemptsByKey.set(key, attempts)
  attemptsByKey.set(key + '|best', bestScore)

  const resp = {
    score,
    totalQuestions: total,
    percentage: Math.round((score / total) * 100),
    attempts,
    bestScore,
    relatedContentTitle: detail.title || '',
    results,
  }

  // keep the student's own result list fresh on this instance
  if (role === 'STUDENT') {
    if (!quizResultsMine) quizResultsMine = (seed.quizResults || []).slice()
    quizResultsMine.unshift({
      id: Date.now() % 1000000,
      contentId: Number(b.contentId) || 0,
      contentTitle: detail.title || '',
      subject: detail.subject || '',
      assignmentId: b.assignmentId || 'none',
      score,
      totalQuestions: total,
      percentage: resp.percentage,
      attempts,
      timeTakenSeconds: Number(b.timeTakenSeconds) || 0,
      submittedAt: new Date().toISOString(),
    })
  }
  return resp
}

// ---------------------------------------------------------------------------
// AI tutor — grounded in the approved lesson material only
// ---------------------------------------------------------------------------
const STOP = new Set(('the a an is are was were of what this that these those in to and or how why do does did i me my with for on it its about explain tell give list show key part parts question questions tell me about').split(' '))

function aiAsk(b) {
  const id = String(b.contentId || '')
  const detail = (seed.details && seed.details[id]) || (seed.detailsTeacher && seed.detailsTeacher[id])
  if (!detail) {
    return {
      answer: 'Pick a lesson first and I will explain it from the approved material.',
      source: 'no lesson selected',
      relatedContentTitle: '',
      engine: 'curriculum-grounded',
    }
  }
  const q = String(b.question || '').toLowerCase()
  const words = q.split(/[^a-z0-9]+/).filter((w) => w.length > 2 && !STOP.has(w))
  let best = null
  let bestScore = 0
  for (const p of detail.parts || []) {
    const hay = ((p.partName || '') + ' ' + (p.label || '') + ' ' + (p.explanation || '')).toLowerCase()
    let s = 0
    for (const w of words) if (hay.indexOf(w) >= 0) s += 1
    if (s > bestScore) { bestScore = s; best = p }
  }
  const source = `Approved lesson material — "${detail.title}"`
  if (best) {
    return {
      answer: `${best.partName}: ${best.explanation || best.label || ''}`.trim(),
      source,
      relatedContentTitle: detail.title,
      engine: 'curriculum-grounded',
    }
  }
  const names = (detail.parts || []).map((p) => p.partName).join(', ')
  const intro = (detail.description || '').split(' In AR')[0].split(' AR ')[0]
  return {
    answer: `${detail.title} — ${intro}${intro ? '.' : ''} Key parts: ${names || 'see the 3D model'}. Ask me about any one of them.`,
    source,
    relatedContentTitle: detail.title,
    engine: 'curriculum-grounded',
  }
}

// ---------------------------------------------------------------------------
// live classroom — sessionId encodes its creation minute so ANY instance can
// rebuild the state (joinCode = sessionId in base36). Student roster is
// per-instance; the 30-minute TTL makes activeSession converge everywhere.
// ---------------------------------------------------------------------------
function makeSessionId() {
  return Math.floor(Date.now() / 60000) * 4096 + Math.floor(Math.random() * 4096)
}
function codeFor(id) {
  return id.toString(36).toUpperCase()
}
function idForCode(code) {
  const n = parseInt(String(code).toUpperCase(), 36)
  return Number.isFinite(n) && n > 0 ? n : null
}
function sessionState(id) {
  const createdMs = Math.floor(id / 4096) * 60000
  const active = !endedIds.has(id) && Date.now() - createdMs < LIVE_TTL_MS
  const students = joinedBySession.get(id) || []
  const cls = (seed.classes && seed.classes[0]) || {}
  const total = Number(cls.studentCount) || students.length || 8
  return {
    sessionId: id,
    joinCode: codeFor(id),
    className: cls.name || 'Grade 10 Science - A',
    assignmentTitle: null,
    activeSession: active,
    totalStudents: total,
    joined: students.length,
    active: students.filter((s) => s.status === 'ACTIVE').length,
    inProgress: 0,
    notStarted: Math.max(0, total - students.length),
    completed: 0,
    students,
  }
}

// ---------------------------------------------------------------------------
module.exports = (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, {})

  const role = roleOf(req)
  const token = String((req.headers && (req.headers.authorization || req.headers.Authorization)) || 'anon')
  const rawPath = (req.url || '').split('?')[0]
  const path = rawPath.replace(/^\/api/, '') || '/'
  const seg = path.split('/').filter(Boolean)
  const m = req.method
  const s0 = seg[0]

  // ---- health ------------------------------------------------------------
  if (m === 'GET' && path === '/health') return send(res, 200, seed.health || { status: 'ok' })

  // ---- auth ---------------------------------------------------------------
  if (m === 'POST' && path === '/auth/login') {
    const b = parseBody(req)
    const email = String(b.email || '').toLowerCase()
    const u = USERS[email]
    if (!u || u.pw !== String(b.password || '')) return send(res, 401, { message: 'Invalid email or password.' })
    return send(res, 200, {
      token: `demo-token-${u.role}`,
      expiresInSeconds: 43200,
      user: { id: u.id, name: u.name, email, role: u.role, grade: u.grade },
    })
  }
  if (m === 'POST' && path === '/auth/register') {
    const b = parseBody(req)
    const r = ROLES.indexOf(String(b.role || '').toUpperCase()) >= 0 ? String(b.role).toUpperCase() : 'STUDENT'
    return send(res, 200, {
      token: `demo-token-${r}`,
      expiresInSeconds: 43200,
      user: {
        id: 100 + Math.floor(Math.random() * 899),
        name: b.name || 'New Student',
        email: String(b.email || '').toLowerCase(),
        role: r,
        grade: b.grade || '',
      },
    })
  }
  if (m === 'GET' && path === '/auth/me') {
    return send(res, 200, {
      user: userForRole(role),
      securityNote: (seed.me && seed.me.securityNote) || '',
    })
  }

  // ---- AR content ---------------------------------------------------------
  if (s0 === 'ar-content') {
    if (m === 'GET' && seg.length === 1) return send(res, 200, seed.contents || [])
    if (m === 'GET' && seg[1] === 'marker' && seg[2]) {
      const d = markerDetail(decodeURIComponent(seg[2]), role)
      return d ? send(res, 200, d) : send(res, 404, { message: `No lesson for marker ${seg[2]}` })
    }
    if (m === 'GET' && seg[1]) {
      const d = detailFor(seg[1], role)
      return d ? send(res, 200, d) : send(res, 404, { message: 'Content not found' })
    }
  }

  // ---- classes ------------------------------------------------------------
  if (m === 'GET' && s0 === 'classes' && seg.length === 1) return send(res, 200, seed.classes || [])
  if (m === 'GET' && s0 === 'classes' && seg[2] === 'roster') {
    const roster = (seed.roster && (seed.roster[seg[1]] || seed.roster['1'])) || []
    return send(res, 200, roster)
  }

  // ---- class schedule (mirrors ClassScheduleController) -------------------
  //   GET    /api/classes/{id}/schedule        list slots for a class
  //   POST   /api/classes/{id}/schedule        add a slot        (TEACHER)
  //   PUT    /api/classes/{id}/schedule/{sid}  update a slot     (TEACHER)
  //   DELETE /api/classes/{id}/schedule/{sid}  remove a slot     (TEACHER)
  //   GET    /api/schedule/day/{DAY}           every slot on a day (TEACHER)
  if (s0 === 'classes' && seg[2] === 'schedule') {
    seedSchedules()
    const classId = Number(seg[1])
    const list = scheduleByClass.get(classId) || []
    if (m === 'GET' && !seg[3]) return send(res, 200, schedulesFor(classId))
    if (m === 'POST' && !seg[3]) {
      if (role !== 'TEACHER') return send(res, 403, { message: 'Only teachers can edit a timetable.' })
      const b = parseBody(req)
      const v = validateSlot(b)
      if (v.error) return send(res, 400, { message: v.error })
      if (list.some((s) => s.dayOfWeek === v.day && s.startTime === v.start)) {
        return send(res, 409, { message: `A slot already exists for ${v.day} at ${v.start}` })
      }
      const created = scheduleDto(classId, scheduleNextId++, v.day, v.start, v.end, b.room, b.notes)
      list.push(created)
      scheduleByClass.set(classId, list)
      return send(res, 201, created)
    }
    if ((m === 'PUT' || m === 'DELETE') && seg[3]) {
      if (role !== 'TEACHER') return send(res, 403, { message: 'Only teachers can edit a timetable.' })
      const sid = Number(seg[3])
      const idx = list.findIndex((s) => s.id === sid)
      if (idx < 0) return send(res, 404, { message: 'Schedule slot not found in this class' })
      if (m === 'DELETE') {
        list.splice(idx, 1)
        scheduleByClass.set(classId, list)
        return send(res, 204, {})
      }
      const v = validateSlot(parseBody(req))
      if (v.error) return send(res, 400, { message: v.error })
      const updated = scheduleDto(classId, sid, v.day, v.start, v.end, parseBody(req).room, parseBody(req).notes)
      list[idx] = updated
      scheduleByClass.set(classId, list)
      return send(res, 200, updated)
    }
  }
  if (m === 'GET' && s0 === 'schedule' && seg[1] === 'day' && seg[2]) {
    const day = String(seg[2]).toUpperCase()
    if (DAY_ORDER.indexOf(day) < 0) return send(res, 400, { message: `Unknown day '${seg[2]}'.` })
    seedSchedules()
    const out = []
    scheduleByClass.forEach((l) => l.forEach((s) => { if (s.dayOfWeek === day) out.push(s) }))
    return send(res, 200, out)
  }

  // ---- assignments --------------------------------------------------------
  if (m === 'GET' && s0 === 'assignments') {
    if (seg[1] === 'student') return send(res, 200, seed.assignmentsStudent || [])
    return send(res, 200, seed.assignments || [])
  }

  // ---- progress / quiz ----------------------------------------------------
  if (m === 'GET' && path === '/progress/me') return send(res, 200, seed.progress || [])
  if (m === 'GET' && path === '/quiz/results/me') {
    if (!quizResultsMine) quizResultsMine = (seed.quizResults || []).slice()
    return send(res, 200, quizResultsMine)
  }
  if (m === 'POST' && path === '/quiz/submit') return send(res, 200, gradeQuiz(role, token, parseBody(req)))

  // ---- analytics ----------------------------------------------------------
  if (m === 'GET' && path === '/analytics/overview') return send(res, 200, seed.analyticsOverview || { teacher: '', classes: [] })
  if (m === 'GET' && s0 === 'analytics' && seg[1] === 'class' && seg[2]) {
    const d = (seed.analyticsClass && (seed.analyticsClass[seg[2]] || seed.analyticsClass['1'])) || null
    return d ? send(res, 200, d) : send(res, 404, { message: 'No analytics for that class' })
  }

  // ---- developer ----------------------------------------------------------
  if (m === 'GET' && path === '/dev/seed-summary') return send(res, 200, seed.seedSummary || {})

  // ---- AI tutor -----------------------------------------------------------
  if (m === 'POST' && path === '/ai/ask') return send(res, 200, aiAsk(parseBody(req)))

  // ---- live classroom -----------------------------------------------------
  if (s0 === 'live') {
    if (m === 'POST' && seg[1] === 'sessions' && !seg[2]) {
      const id = makeSessionId()
      const st = sessionState(id)
      return send(res, 200, {
        sessionId: id,
        joinCode: st.joinCode,
        className: st.className,
        totalStudents: st.totalStudents,
      })
    }
    if (m === 'GET' && seg[1] === 'sessions' && !seg[2]) {
      return send(res, 200, Array.from(joinedBySession.keys()).map(sessionState))
    }
    if (m === 'GET' && seg[1] === 'sessions' && seg[2]) {
      // the frontend polls with the numeric sessionId from the create response
      const id = Number(seg[2])
      if (!Number.isFinite(id) || id <= 0) return send(res, 404, { message: 'Session not found' })
      return send(res, 200, sessionState(id))
    }
    if (m === 'POST' && seg[1] === 'sessions' && seg[3] === 'join') {
      // /api/live/sessions/{joinCode}/join  (joinCode is sessionId in base36)
      const id = idForCode(seg[2] || '')
      if (!id) return send(res, 404, { message: 'No active session with that code.' })
      const st = sessionState(id)
      if (!st.activeSession) return send(res, 404, { message: 'That session has ended.' })
      const list = joinedBySession.get(id) || []
      if (!list.some((x) => x.studentId === 3)) {
        list.push({ studentId: 3, name: 'Ravi Sharma', status: 'ACTIVE', completion: 40, lastHeartbeat: new Date().toISOString() })
        joinedBySession.set(id, list)
      }
      return send(res, 200, { sessionId: id, studentId: 3, assignmentId: null })
    }
    if (m === 'POST' && seg[1] === 'sessions' && seg[3] === 'heartbeat') {
      const id = Number(seg[2])
      const list = Number.isFinite(id) ? joinedBySession.get(id) || [] : []
      list.forEach((x) => { if (x.studentId === 3) x.lastHeartbeat = new Date().toISOString() })
      if (Number.isFinite(id)) joinedBySession.set(id, list)
      return send(res, 200, { ok: true, activeSession: Number.isFinite(id) ? sessionState(id).activeSession : false })
    }
    if (m === 'POST' && seg[1] === 'sessions' && seg[3] === 'end') {
      const id = Number(seg[2])
      if (Number.isFinite(id)) endedIds.add(id)
      return send(res, 200, { sessionId: id, activeSession: false })
    }
  }

  return send(res, 404, { message: `No API route for ${m} /api${path}` })
}
