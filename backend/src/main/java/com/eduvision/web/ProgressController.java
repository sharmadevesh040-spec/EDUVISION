package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Learning-progress tracking written by the AR viewer as a student explores a lesson. */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressRepository progress;
    private final ArContentRepository contents;
    private final AssignmentRepository assignments;
    private final CurrentUser currentUser;

    public ProgressController(ProgressRepository progress, ArContentRepository contents,
                              AssignmentRepository assignments, CurrentUser currentUser) {
        this.progress = progress;
        this.contents = contents;
        this.assignments = assignments;
        this.currentUser = currentUser;
    }

    public record ProgressRequest(@NotNull Long contentId, Long assignmentId,
                                  @Min(0) long timeSpentSeconds, @Min(0) int completion) {
    }

    public record ProgressDto(Long id, Long contentId, String contentTitle, String subject,
                              Long assignmentId, String assignmentTitle, long timeSpentSeconds,
                              int completion, ProgressStatus status, int sessionCount,
                              String lastAccessed) {
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ProgressDto save(@RequestBody ProgressRequest req) {
        User me = currentUser.get();
        ArContent content = requireContent(req.contentId());
        Assignment assignment = req.assignmentId() == null ? null : requireAssignment(req.assignmentId());

        Progress p = progress.findByStudentAndContentAndAssignment(me, content, assignment)
                .orElseGet(() -> new Progress(me, content, assignment));

        p.timeSpentSeconds += Math.max(0, req.timeSpentSeconds());
        p.completion = Math.clamp(Math.max(p.completion, req.completion()), 0, 100);
        p.status = p.completion >= 100 ? ProgressStatus.COMPLETED
                : (p.completion > 0 || p.sessionCount > 0 ? ProgressStatus.IN_PROGRESS : ProgressStatus.NOT_STARTED);
        p.sessionCount += 1;
        p.lastAccessed = Instant.now();
        progress.save(p);
        return toDto(p);
    }

    /** Cheap keep-alive: the AR viewer calls this while a session is open. */
    @PostMapping("/heartbeat")
    @PreAuthorize("hasRole('STUDENT')")
    public ProgressDto heartbeat(@RequestBody ProgressRequest req) {
        User me = currentUser.get();
        ArContent content = requireContent(req.contentId());
        Assignment assignment = req.assignmentId() == null ? null : requireAssignment(req.assignmentId());

        Progress p = progress.findByStudentAndContentAndAssignment(me, content, assignment)
                .orElseGet(() -> new Progress(me, content, assignment));
        p.sessionCount += 1;
        p.lastAccessed = Instant.now();
        if (p.completion > 0 && p.status == ProgressStatus.NOT_STARTED) {
            p.status = ProgressStatus.IN_PROGRESS;
        }
        progress.save(p);
        return toDto(p);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ProgressDto> mine() {
        return progress.findByStudentOrderByLastAccessedDesc(currentUser.get()).stream()
                .map(this::toDto).toList();
    }

    @GetMapping("/content/{contentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ProgressDto> forContent(@PathVariable Long contentId) {
        User me = currentUser.get();
        return progress.findByStudentOrderByLastAccessedDesc(me).stream()
                .filter(p -> p.content.id.equals(contentId))
                .map(this::toDto).toList();
    }

    private ArContent requireContent(Long id) {
        return contents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AR content not found"));
    }

    private Assignment requireAssignment(Long id) {
        return assignments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
    }

    private ProgressDto toDto(Progress p) {
        Map<String, Object> ignored = new LinkedHashMap<>();
        return new ProgressDto(p.id, p.content.id, p.content.title, p.content.subject.name(),
                p.assignment == null ? null : p.assignment.id,
                p.assignment == null ? null : p.assignment.title,
                p.timeSpentSeconds, p.completion, p.status, p.sessionCount, p.lastAccessed.toString());
    }
}