package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Teacher-created AR activities assigned to a class. */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentRepository assignments;
    private final EduClassRepository classes;
    private final ArContentRepository contents;
    private final ClassMemberRepository members;
    private final ProgressRepository progress;
    private final CurrentUser currentUser;

    public AssignmentController(AssignmentRepository assignments, EduClassRepository classes,
                                ArContentRepository contents, ClassMemberRepository members,
                                ProgressRepository progress, CurrentUser currentUser) {
        this.assignments = assignments;
        this.classes = classes;
        this.contents = contents;
        this.members = members;
        this.progress = progress;
        this.currentUser = currentUser;
    }

    public record CreateAssignmentRequest(@NotNull Long classId, @NotNull Long contentId,
                                          @NotBlank String title, String instructions,
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deadline) {
    }

    public record AssignmentDto(Long id, String title, String instructions, String deadline,
                                AssignmentStatus status, Long classId, String className,
                                Long contentId, String contentTitle, String subject,
                                String modelUrl, String markerId, int completion, String lastAccessed) {
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AssignmentDto> create(@Valid @RequestBody CreateAssignmentRequest req) {
        User me = currentUser.get();
        EduClass c = classes.findById(req.classId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        if (!c.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }
        ArContent content = contents.findById(req.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AR content not found"));
        Assignment a = assignments.save(new Assignment(req.title().trim(), req.instructions(),
                req.deadline(), c, content, me));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(a, null));
    }

    @GetMapping
    public List<AssignmentDto> list() {
        User me = currentUser.get();
        List<Assignment> rows = switch (me.role) {
            case TEACHER -> assignments.findByTeacherOrderByIdAsc(me);
            case STUDENT -> members.findByStudent(me).stream()
                    .flatMap(m -> assignments.findByEduClassOrderByIdAsc(m.eduClass).stream())
                    .distinct().toList();
            case DEVELOPER -> List.of();
        };
        return rows.stream().map(a -> toDto(a, me)).toList();
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AssignmentDto> forStudent() {
        User me = currentUser.get();
        return members.findByStudent(me).stream()
                .flatMap(m -> assignments.findByEduClassOrderByIdAsc(m.eduClass).stream())
                .distinct()
                .map(a -> toDto(a, me))
                .toList();
    }

    @GetMapping("/{id}")
    public AssignmentDto detail(@PathVariable Long id) {
        Assignment a = require(id);
        return toDto(a, currentUser.get());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('STUDENT')")
    public AssignmentDto updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Assignment a = require(id);
        User me = currentUser.get();
        boolean enrolled = members.existsByEduClassAndStudent(a.eduClass, me);
        if (!enrolled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not enrolled in this class");
        }
        try {
            a.status = AssignmentStatus.valueOf(body.getOrDefault("status", "").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be NOT_STARTED, IN_PROGRESS or COMPLETED");
        }
        assignments.save(a);
        return toDto(a, me);
    }

    private Assignment require(Long id) {
        return assignments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
    }

    private AssignmentDto toDto(Assignment a, User viewer) {
        Map<String, Object> p = new LinkedHashMap<>();
        int completion = 0;
        String lastAccessed = "never";
        if (viewer != null) {
            var row = progress.findByStudentAndContentAndAssignment(viewer, a.content, a);
            if (row.isPresent()) {
                completion = row.get().completion;
                lastAccessed = row.get().lastAccessed.toString();
                p.put("status", row.get().status.name());
            }
        }
        return new AssignmentDto(a.id, a.title, a.instructions, String.valueOf(a.deadline),
                viewer != null && p.containsKey("status") ? (AssignmentStatus) p.get("status") : a.status,
                a.eduClass.id, a.eduClass.name, a.content.id, a.content.title,
                a.content.subject.name(), a.content.modelUrl, a.content.markerId, completion, lastAccessed);
    }
}