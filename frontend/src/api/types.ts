/**
 * DTOs for the AR EduVision Spring Boot API.
 * Field names mirror the Java records / Map payloads in
 * `backend/src/main/java/com/eduvision/web/*.java` — do not guess.
 */

export type Role = 'TEACHER' | 'STUDENT' | 'DEVELOPER'

/** AuthController.UserDto */
export interface User {
  id: number
  name: string
  email: string
  role: Role
  grade: string | null
}

/** AuthController.AuthResponse */
export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

/** AuthController.register body */
export interface RegisterPayload {
  name: string
  email: string
  password: string
  role: Role
  grade?: string
}

/** ClassController.ClassDto */
export interface ClassDto {
  id: number
  name: string
  grade: string | null
  section: string | null
  teacherName: string | null
  studentCount: number
  assignmentCount: number
}

/** ClassController roster row (Map payload) */
export interface RosterRow {
  studentId: number
  name: string
  email: string
  completedAssignments: number
  totalAssignments: number
  avgQuizScore: number
  timeSpentSeconds: number
  lastAccessed: string
}

/** AnalyticsController.overview() -> per-class entry */
export interface OverviewClass {
  classId: number
  className: string
  students: number
  assignments: number
  engagementPercent: number
  avgQuizScore: number
}

/** AnalyticsController.overview() */
export interface AnalyticsOverview {
  teacher: string
  classes: OverviewClass[]
}

/** ArContentController.ContentSummary */
export interface ContentSummary {
  id: number
  title: string
  subject: string
  grade: string | null
  description: string | null
  modelUrl: string
  markerId: string | null
  status: 'DRAFT' | 'PUBLISHED' | string
  version: number
  partCount: number
  questionCount: number
  createdBy: string | null
}

/** AssignmentController.AssignmentDto */
export interface AssignmentDto {
  id: number
  title: string
  instructions: string | null
  deadline: string | null
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | string
  classId: number
  className: string
  contentId: number
  contentTitle: string
  subject: string
  modelUrl: string
  markerId: string | null
  completion: number
  lastAccessed: string
}

/** ProgressController.ProgressDto */
export interface ProgressDto {
  id: number
  contentId: number
  contentTitle: string
  subject: string
  assignmentId: number | null
  assignmentTitle: string | null
  timeSpentSeconds: number
  completion: number
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | string
  sessionCount: number
  lastAccessed: string
}

/** QuizController.myResults() row */
export interface QuizResultRow {
  id: number
  contentId: number
  contentTitle: string
  subject: string
  assignmentId: number | 'none'
  score: number
  totalQuestions: number
  percentage: number
  attempts: number
  timeTakenSeconds: number
  submittedAt: string
}

/** DevController.seedSummary() */
export interface SeedSummary {
  users: number
  classes: number
  contents: number
  parts: number
  questions: number
  assignments: number
  classMembers: number
}

/** HealthController */
export interface HealthPayload {
  status: string
}

/**
 * ArContentController.PartDto — one tappable annotation on the 3D model.
 * The backend does not currently send a hotspot position; the optional fields
 * below are honoured by the viewer if/when it does, otherwise parts are
 * auto-placed around the model bounding box.
 */
export interface PartDto {
  id: number
  partName: string
  label: string | null
  explanation: string | null
  audioUrl: string | null
  /** Model-space hotspot anchor, e.g. "0.12 0.4 -0.08". */
  position?: string | null
  positionX?: number | null
  positionY?: number | null
  positionZ?: number | null
}

/** ArContentController.QuestionDto (correctOption/explanation are null for students). */
export interface QuestionDto {
  id: number
  questionText: string
  options: string[]
  correctOption: string | null
  explanation: string | null
  topic: string | null
}

/** ArContentController.ContentDetail — GET /api/ar-content/{id} and /marker/{markerId}. */
export interface ContentDetail {
  id: number
  title: string
  subject: string
  grade: string | null
  description: string | null
  modelUrl: string
  markerId: string | null
  audioUrl: string | null
  videoUrl: string | null
  status: 'DRAFT' | 'PUBLISHED' | string
  version: number
  createdBy: string | null
  trackingTestReport: string | null
  averageRating: number
  parts: PartDto[]
  questions: QuestionDto[]
}
