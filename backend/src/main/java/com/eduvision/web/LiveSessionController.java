package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Live classroom mode: the teacher opens a session and broadcasts a join code; students join, work
 * through the AR activity and heartbeat. The teacher sees live counts (joined / active / in progress
 * / not started).
 */
@RestController
@RequestMapping("/api/live")
public class LiveSessionController {

    /** A student counts as "active" when their heartbeat is more recent than this. */
    private static final Duration ACTIVE_WINDOW = Duration.ofSeconds(60);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final LiveSessionRepository sessions;
    private final LiveParticipantRepository participants;
    private final EduClassRepository classes;
    private final AssignmentRepository assignments;
    private final ClassMemberRepository members;
    private final CurrentUser currentUser;
    private final SecureRandom random = new SecureRandom();

    public LiveSessionController(LiveSessionRepository sessions, LiveParticipantRepository participants,
                                 EduClassRepository classes, AssignmentRepository assignments,
                                 ClassMemberRepository members, CurrentUser currentUser) {
        this.sessions = sessions;
        this.participants = participants;
        this.classes = classes;
        this.assignments = assignments;
        this.members = members;
        this.currentUser = currentUser;
    }

    public record StartRequest(@NotNull Long classId, Long assignmentId) {
    }

    public record HeartbeatRequest(ProgressStatus status, Integer completion) {
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, Object> start(@Valid @RequestBody StartRequest req) {
        User me = currentUser.get();
        EduClass c = classes.findById(req.classId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        if (!c.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }
        Assignment assignment = null;
        if (req.assignmentId() != null) {
            assignment = assignments.findById(req.assignmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        }
        LiveSession session = sessions.save(new LiveSession(joinCode(), c, assignment, me));
        return Map.of("sessionId", session.id, "joinCode", session.joinCode,
                "className", c.name, "totalStudents", members.findByEduClass(c).size());
    }

    @PostMapping("/sessions/{joinCode}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> join(@PathVariable String joinCode) {
        User me = currentUser.get();
        LiveSession session = sessions.findByJoinCode(joinCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown join code"));
        if (!session.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That live session has ended");
        }
        if (!members.existsByEduClassAndStudent(session.eduClass, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not enrolled in this class");
        }
        participants.findBySessionAndStudent(session, me)
                .orElseGet(() -> participants.save(new LiveParticipant(session, me)));
        return Map.of("sessionId", session.id, "studentId", me.id,
                "assignmentId", session.assignment == null ? "none" : session.assignment.id);
    }

    @PostMapping("/sessions/{sessionId}/heartbeat")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<LiveParticipant> heartbeat(@PathVariable Long sessionId,
                                                    @RequestBody HeartbeatRequest req) {
        User me = currentUser.get();
        LiveSession session = require(sessionId);
        LiveParticipant p = participants.findBySessionAndStudent(session, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Join the session first"));
        if (req.status() != null) p.status = req.status();
        if (req.completion() != null) p.completion = Math.clamp(req.completion(), 0, 100);
        p.lastHeartbeat = Instant.now();
        participants.save(p);
        return ResponseEntity.ok(p);
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public Map<String, Object> liveState(@PathVariable Long sessionId) {
        User me = currentUser.get();
        LiveSession session = require(sessionId);
        boolean teacher = session.teacher.id.equals(me.id);
        if (!teacher && !members.existsByEduClassAndStudent(session.eduClass, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this class");
        }

        Instant activeAfter = Instant.now().minus(ACTIVE_WINDOW);
        List<LiveParticipant> rows = participants.findBySession(session);
        int total = members.findByEduClass(session.eduClass).size();
        int joined = rows.size();
        int active = (int) rows.stream().filter(p -> p.lastHeartbeat.isAfter(activeAfter)).count();
        int notStarted = (int) rows.stream().filter(p -> p.status == ProgressStatus.NOT_STARTED).count();
        int inProgress = (int) rows.stream().filter(p -> p.status == ProgressStatus.IN_PROGRESS).count();
        int completed = (int) rows.stream().filter(p -> p.status == ProgressStatus.COMPLETED).count();

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("sessionId", session.id);
        out.put("joinCode", session.joinCode);
        out.put("className", session.eduClass.name);
        out.put("assignmentTitle",
                session.assignment == null ? "Free exploration" : session.assignment.title);
        out.put("activeSession", session.active);
        out.put("totalStudents", total);
        out.put("joined", joined);
        out.put("active", active);
        out.put("inProgress", inProgress);
        out.put("notStarted", notStarted);
        out.put("completed", completed);
        out.put("students", rows.stream().map(p -> {
            Map<String, Object> s = new java.util.LinkedHashMap<>();
            s.put("studentId", p.student.id);
            s.put("name", p.student.name);
            s.put("status", p.status.name());
            s.put("completion", p.completion);
            s.put("lastHeartbeat", p.lastHeartbeat.toString());
            return s;
        }).toList());
        return out;
    }

    @PostMapping("/sessions/{sessionId}/end")
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, Object> end(@PathVariable Long sessionId) {
        LiveSession session = require(sessionId);
        if (!session.teacher.id.equals(currentUser.get().id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the session owner can end it");
        }
        session.active = false;
        session.endedAt = Instant.now();
        sessions.save(session);
        return Map.of("sessionId", session.id, "active", false);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String, Object>> myActiveSessions() {
        return sessions.findByActiveTrue().stream()
                .filter(s -> s.teacher.id.equals(currentUser.get().id))
                .map(s -> Map.<String, Object>of("sessionId", s.id, "joinCode", s.joinCode,
                        "className", s.eduClass.name, "startedAt", s.startedAt.toString(),
                        "joined", participants.findBySession(s).size()))
                .toList();
    }

    private LiveSession require(Long id) {
        return sessions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Live session not found"));
    }

    private String joinCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}