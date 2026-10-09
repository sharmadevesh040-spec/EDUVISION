package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Class and roster management for teachers. */
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final EduClassRepository classes;
    private final ClassMemberRepository members;
    private final UserRepository users;
    private final AssignmentRepository assignments;
    private final ProgressRepository progress;
    private final QuizResultRepository quizResults;
    private final CurrentUser currentUser;

    public ClassController(EduClassRepository classes, ClassMemberRepository members, UserRepository users,
                           AssignmentRepository assignments, ProgressRepository progress,
                           QuizResultRepository quizResults, CurrentUser currentUser) {
        this.classes = classes;
        this.members = members;
        this.users = users;
        this.assignments = assignments;
        this.progress = progress;
        this.quizResults = quizResults;
        this.currentUser = currentUser;
    }

    public record CreateClassRequest(@NotBlank String name, String grade, String section) {
    }

    public record AddMemberRequest(@NotBlank String studentEmail) {
    }

    public record ClassDto(Long id, String name, String grade, String section,
                           String teacherName, int studentCount, int assignmentCount) {
    }

    public record MemberDto(Long studentId, String name, String email, String grade, String joinedAt) {
    }

    @GetMapping
    public List<ClassDto> list() {
        User me = currentUser.get();
        List<EduClass> visible = me.role == Role.TEACHER
                ? classes.findByTeacher(me)
                : members.findByStudent(me).stream().map(m -> m.eduClass).toList();
        return visible.stream().map(this::toDto).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassDto> create(@Valid @RequestBody CreateClassRequest req) {
        EduClass c = classes.save(new EduClass(req.name().trim(), req.grade(), req.section(), currentUser.get()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(c));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        EduClass c = requireClass(id);
        return Map.of(
                "class", toDto(c),
                "members", members.findByEduClass(c).stream()
                        .map(m -> new MemberDto(m.student.id, m.student.name, m.student.email,
                                m.student.grade, m.joinedAt.toString()))
                        .toList(),
                "assignments", assignments.findByEduClassOrderByIdAsc(c).stream()
                        .map(a -> Map.of("id", a.id, "title", a.title,
                                "status", a.status.name(), "deadline", String.valueOf(a.deadline),
                                "contentTitle", a.content.title))
                        .toList());
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<MemberDto> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest req) {
        EduClass c = requireClass(id);
        if (!c.teacher.getId().equals(currentUser.get().id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }
        User student = users.findByEmailIgnoreCase(req.studentEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));
        if (student.role != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only students can be enrolled in a class");
        }
        members.findByEduClassAndStudent(c, student)
                .orElseGet(() -> members.save(new ClassMember(c, student)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MemberDto(student.id, student.name, student.email, student.grade, Instant.now().toString()));
    }

    @DeleteMapping("/{id}/members/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long studentId) {
        EduClass c = requireClass(id);
        members.findByEduClassAndStudent(c, users.findById(studentId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found")))
                .ifPresent(members::delete);
        return ResponseEntity.noContent().build();
    }

    /** Roster with a per-student activity summary so a teacher can spot who needs support. */
    @GetMapping("/{id}/roster")
    @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String, Object>> roster(@PathVariable Long id) {
        EduClass c = requireClass(id);
        List<Assignment> classAssignments = assignments.findByEduClassOrderByIdAsc(c);
        return members.findByEduClass(c).stream().map(m -> {
            User s = m.student;
            List<Progress> rows = progress.findByStudentOrderByLastAccessedDesc(s);
            long timeSpent = rows.stream().mapToLong(p -> p.timeSpentSeconds).sum();
            List<QuizResult> results = quizResults.findByStudentOrderBySubmittedAtDesc(s);
            double avgScore = results.stream().mapToInt(r -> r.score).average().orElse(0);
            return Map.<String, Object>of(
                    "studentId", s.id,
                    "name", s.name,
                    "email", s.email,
                    "completedAssignments", classAssignments.stream()
                            .filter(a -> rows.stream().anyMatch(p -> p.assignment != null
                                    && a.id.equals(p.assignment.id) && p.completion >= 100)).count(),
                    "totalAssignments", classAssignments.size(),
                    "avgQuizScore", Math.round(avgScore),
                    "timeSpentSeconds", timeSpent,
                    "lastAccessed", rows.stream().map(p -> p.lastAccessed.toString())
                            .findFirst().orElse("never"));
        }).toList();
    }

    private EduClass requireClass(Long id) {
        return classes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    private ClassDto toDto(EduClass c) {
        return new ClassDto(c.id, c.name, c.grade, c.section, c.teacher.name,
                members.findByEduClass(c).size(), assignments.findByEduClass(c).size());
    }
}