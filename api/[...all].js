// Vercel serverless catch-all that mirrors the EduVision backend API.
// It exists so the React app works on Vercel TODAY (login + AR content +
// models) before the Java jar is deployed anywhere. GLB models themselves
// are fetched by the browser from jsdelivr, so they just work.
//
// Route: /api/*  (file location: api/[...all].js)
const seed = require('./seed.json')

// ── In-memory schedule store (resets on cold-start, fine for the mock) ──────
const SCHEDULES = [
  { id: 1, classId: 1, className: 'Grade 10 Science - A', grade: '10', section: 'A',
    dayOfWeek: 'MONDAY',    startTime: '09:00', endTime: '10:00', room: 'Room 101', notes: 'Introduction to AR concepts' },
  { id: 2, classId: 1, className: 'Grade 10 Science - A', grade: '10', section: 'A',
    dayOfWeek: 'MONDAY',    startTime: '11:00', endTime: '12:00', room: 'Lab A',    notes: 'Hands-on AR session — bring your device' },
  { id: 3, classId: 1, className: 'Grade 10 Science - A', grade: '10', section: 'A',
    dayOfWeek: 'WEDNESDAY', startTime: '09:00', endTime: '10:00', room: 'Room 101', notes: 'Theory: Optics & Physics of AR' },
  { id: 4, classId: 1, className: 'Grade 10 Science - A', grade: '10', section: 'A',
    dayOfWeek: 'WEDNESDAY', startTime: '14:00', endTime: '15:00', room: 'Lab A',    notes: 'Quiz review and model exploration' },
  { id: 5, classId: 1, className: 'Grade 10 Science - A', grade: '10', section: 'A',
    dayOfWeek: 'FRIDAY',    startTime: '10:00', endTime: '11:30', room: 'Room 205', notes: 'Weekly assessment & assignment hand-in' },
]
let scheduleNextId = 6

const USERS = {
  'student1@eduvision.com':  { id: 3, name: 'Ravi Sharma',        role: 'STUDENT',   grade: '10', pw: 'Student@123' },
  'teacher@eduvision.com':   { id: 1, name: 'Ms. Elena Fischer',  role: 'TEACHER',   grade: '10', pw: 'Teacher@123' },
  'developer@eduvision.com': { id: 2, name: 'Dev Arjun Mehta',    role: 'DEVELOPER', grade: '',   pw: 'Developer@123' },
}

function toArray(val) {
  if (Array.isArray(val)) return val
  if (val && Array.isArray(val.value)) return val.value
  if (val && typeof val === 'object' && Object.keys(val).length > 0) return [val]
  return []
}

const CLASSES = toArray(seed.classes)
const ASSIGNMENTS = toArray(seed.assignments)
const PROGRESS = toArray(seed.progress)
const QUIZ_RESULTS = toArray(seed.quizResults)

function send(res, code, obj) {
  res.statusCode = code
  res.setHeader('Content-Type', 'application/json')
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization')
  res.end(JSON.stringify(obj))
}

async function parseBody(req) {
  if (req.body && typeof req.body === 'object') return req.body
  if (typeof req.body === 'string') {
    try { return JSON.parse(req.body) } catch { return {} }
  }
  if (Buffer.isBuffer(req.body)) {
    try { return JSON.parse(req.body.toString('utf8')) } catch { return {} }
  }
  return new Promise((resolve) => {
    let data = ''
    req.on('data', chunk => { data += chunk })
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}) }
      catch { resolve({}) }
    })
    req.on('error', () => resolve({}))
  })
}

function detailByMarker(markerId) {
  for (const key of Object.keys(seed.details || {})) {
    const d = seed.details[key]
    if (String(d.markerId) === String(markerId)) return d
  }
  return null
}

module.exports = async (req, res) => {
  try {
    if (req.method === 'OPTIONS') return send(res, 204, {})

    const rawPath = (req.url || '').split('?')[0].replace(/^\/api/, '') || '/'
    const path = rawPath.length > 1 && rawPath.endsWith('/') ? rawPath.slice(0, -1) : rawPath
    const seg = path.split('/').filter(Boolean)
    const m = req.method

    if (m === 'GET' && path === '/health') return send(res, 200, { status: 'ok' })

    // ---- auth
    if (m === 'POST' && path === '/auth/login') {
      const b = await parseBody(req)
      const email = String(b.email || '').toLowerCase().trim()
      const u = USERS[email]
      if (!u || u.pw !== String(b.password || '')) {
        return send(res, 401, { message: 'Invalid email or password.' })
      }
      return send(res, 200, {
        token: `demo-token-${u.role}`,
        expiresInSeconds: 43200,
        user: { id: u.id, name: u.name, email, role: u.role, grade: u.grade }
      })
    }
    if (m === 'POST' && path === '/auth/register') {
      const b = await parseBody(req)
      const role = b.role || 'STUDENT'
      const email = String(b.email || '').toLowerCase().trim()
      return send(res, 200, {
        token: `demo-token-${role}`,
        expiresInSeconds: 43200,
        user: { id: 99, name: b.name || 'Student', email, role, grade: b.grade || '' }
      })
    }
    if (m === 'GET' && path === '/auth/me') return send(res, 200, seed.me || {})

    // ---- ar content
    if (m === 'GET' && seg[0] === 'ar-content') {
      if (seg.length === 1) return send(res, 200, seed.contents || [])
      if (seg[1] === 'marker' && seg[2]) {
        const d = detailByMarker(seg[2])
        return d ? send(res, 200, d) : send(res, 404, { message: `No lesson for marker ${seg[2]}` })
      }
      const d = (seed.details || {})[String(seg[1])]
      return d ? send(res, 200, d) : send(res, 404, { message: 'Content not found' })
    }
    if (m === 'POST' && seg[0] === 'ar-content') {
      const b = await parseBody(req)
      return send(res, 201, { id: Date.now(), ...b })
    }

    // ---- classes & roster
    if (m === 'GET' && seg[0] === 'classes') {
      if (seg.length === 1) return send(res, 200, CLASSES)
      if (seg[2] === 'roster') {
        return send(res, 200, [
          { id: 3, name: 'Ravi Sharma', email: 'student1@eduvision.com', role: 'STUDENT', grade: '10' }
        ])
      }
      const c = CLASSES.find(x => String(x.id) === String(seg[1]))
      return c ? send(res, 200, c) : send(res, 404, { message: 'Class not found' })
    }
    if (m === 'POST' && seg[0] === 'classes') {
      const b = await parseBody(req)
      return send(res, 201, { id: Date.now(), ...b })
    }

    // ---- assignments
    if (m === 'GET' && seg[0] === 'assignments') {
      return send(res, 200, ASSIGNMENTS)
    }
    if (m === 'POST' && seg[0] === 'assignments') {
      const b = await parseBody(req)
      return send(res, 201, { id: Date.now(), ...b })
    }

    // ---- progress / quiz
    if (m === 'GET' && path === '/progress/me') return send(res, 200, PROGRESS)
    if (m === 'GET' && path === '/quiz/results/me') return send(res, 200, QUIZ_RESULTS)
    if (m === 'POST' && path === '/quiz/submit') {
      return send(res, 200, {
        score: 3,
        totalQuestions: 3,
        percentage: 100,
        attempts: 1,
        bestScore: 3,
        relatedContentTitle: 'Damaged Flight Helmet',
        results: []
      })
    }

    // ---- dev & analytics
    if (m === 'GET' && path === '/dev/seed-summary') {
      return send(res, 200, {
        users: 3,
        classes: CLASSES.length,
        contents: (seed.contents || []).length,
        assignments: ASSIGNMENTS.length,
        seededAt: new Date().toISOString()
      })
    }
    if (m === 'GET' && seg[0] === 'analytics') {
      if (seg[1] === 'overview') {
        return send(res, 200, {
          totalStudents: 32,
          activeStudents: 28,
          averageQuizScore: 84.5,
          averageEngagementScore: 78.2,
          totalEngagedMinutes: 480,
          classes: CLASSES.map(c => ({
            id: c.id,
            name: c.name,
            grade: c.grade,
            subject: c.subject || 'SCIENCE',
            studentCount: c.studentCount || 16,
            avgScore: 82.0,
            avgEngagement: 76.5
          }))
        })
      }
      if (seg[1] === 'class') {
        return send(res, 200, {
          classId: Number(seg[2]) || 1,
          className: 'Grade 10 Physics',
          studentCount: 16,
          averageScore: 82.0,
          averageEngagement: 76.5,
          students: [
            { id: 3, name: 'Ravi Sharma', email: 'student1@eduvision.com', quizScore: 88, engagementScore: 92, status: 'ENGAGED' }
          ]
        })
      }
    }

    // ---- live sessions
    if (m === 'POST' && path === '/live/sessions') return send(res, 200, { sessionId: 1, code: 'EDU1', joinCode: 'EDU1', className: 'Physics' })
    if (m === 'GET' && seg[0] === 'live') return send(res, 200, { sessionId: 1, status: 'ACTIVE', code: 'EDU1' })
    if (m === 'POST' && seg[0] === 'live') return send(res, 200, { ok: true, sessionId: 1 })

    // ---- class schedules
    // GET  /api/classes/:classId/schedule         → list slots for a class
    // POST /api/classes/:classId/schedule         → add a slot
    // PUT  /api/classes/:classId/schedule/:id     → update a slot
    // DELETE /api/classes/:classId/schedule/:id   → remove a slot
    // GET  /api/schedule/day/:day                 → all slots on a given day
    if (seg[0] === 'classes' && seg[2] === 'schedule') {
      const classId = Number(seg[1])
      const slotId  = seg[3] ? Number(seg[3]) : null

      if (m === 'GET' && !slotId) {
        return send(res, 200, SCHEDULES.filter(s => s.classId === classId))
      }

      if (m === 'POST' && !slotId) {
        const b = await parseBody(req)
        const slot = {
          id: scheduleNextId++,
          classId,
          className: 'Grade 10 Science - A',
          grade: '10',
          section: 'A',
          dayOfWeek: b.dayOfWeek || 'MONDAY',
          startTime: b.startTime || '09:00',
          endTime:   b.endTime   || '10:00',
          room:      b.room  || null,
          notes:     b.notes || null,
        }
        SCHEDULES.push(slot)
        return send(res, 201, slot)
      }

      if (m === 'PUT' && slotId) {
        const idx = SCHEDULES.findIndex(s => s.id === slotId && s.classId === classId)
        if (idx === -1) return send(res, 404, { message: 'Schedule slot not found' })
        const b = await parseBody(req)
        SCHEDULES[idx] = {
          ...SCHEDULES[idx],
          dayOfWeek: b.dayOfWeek ?? SCHEDULES[idx].dayOfWeek,
          startTime: b.startTime ?? SCHEDULES[idx].startTime,
          endTime:   b.endTime   ?? SCHEDULES[idx].endTime,
          room:      b.room  !== undefined ? b.room  : SCHEDULES[idx].room,
          notes:     b.notes !== undefined ? b.notes : SCHEDULES[idx].notes,
        }
        return send(res, 200, SCHEDULES[idx])
      }

      if (m === 'DELETE' && slotId) {
        const idx = SCHEDULES.findIndex(s => s.id === slotId && s.classId === classId)
        if (idx === -1) return send(res, 404, { message: 'Schedule slot not found' })
        SCHEDULES.splice(idx, 1)
        return send(res, 204, {})
      }
    }

    // GET /api/schedule/day/:day  → all slots across all classes on a given day
    if (m === 'GET' && seg[0] === 'schedule' && seg[1] === 'day' && seg[2]) {
      const day = String(seg[2]).toUpperCase()
      return send(res, 200, SCHEDULES.filter(s => s.dayOfWeek === day))
    }

    return send(res, 404, { message: `No API route for ${m} /api${path}` })
  } catch (err) {
    return send(res, 500, { message: err instanceof Error ? err.message : 'Internal Server Error' })
  }
}
