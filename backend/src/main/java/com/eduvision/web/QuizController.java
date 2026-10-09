package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side quiz grading.
 *
 * <p>The client only ever sends question ids and the option the student picked. The score is computed
 * here from the stored {@code correctOption}, so a tampered request cannot inflate a result.
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizQuestionRepository questions;
    private final QuizResultRepository results;
    private final ArContentRepository contents;
    private final AssignmentRepository assignments;
    private final ClassMemberRepository members;
    private final CurrentUser currentUser;

    public QuizController(QuizQuestionRepository questions, QuizResultRepository results,
                          ArContentRepository contents, AssignmentRepository assignments,
                          ClassMemberRepository members, CurrentUser currentUser) {
        this.questions = questions;
        this.results = results;
        this.contents = contents;
        this.assignments = assignments;
        this.members = members;
        this.currentUser = currentUser;
    }

    public record Answer(@NotNull Long questionId, String selected) {
    }

    public record SubmitRequest(Long assignmentId, @NotNull Long contentId,
                                @NotEmpty List<@Valid Answer> answers, long timeTakenSeconds) {
    }

    public record AnswerResult(Long questionId, String selected, boolean correct,
                               String correctOption, String explanation, String topic) {
    }

    public record SubmitResponse(int score, int totalQuestions, int percentage, int attempts,
                                 int bestScore, String relatedContentTitle, List<AnswerResult> results) {
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public SubmitResponse submit(@Valid @RequestBody SubmitRequest req) {
        User me = currentUser.get();
        ArContent content = contents.findById(req.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AR content not found"));

        Assignment assignment = null;
        if (req.assignmentId() != null) {
            assignment = assignments.findById(req.assignmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
            if (!members.existsByEduClassAndStudent(assignment.eduClass, me)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not enrolled in this class");
            }
        }

        List<QuizQuestion> bank = questions.findByContentOrderByOrderIndexAsc(content);
        Map<Long, QuizQuestion> byId = new LinkedHashMap<>();
        bank.forEach(q -> byId.put(q.id, q));

        List<AnswerResult> graded = new ArrayList<>();
        int score = 0;
        for (Answer a : req.answers()) {
            QuizQuestion q = byId.get(a.questionId());
            if (q == null) {
                continue; // ignore questions that do not belong to this lesson
            }
            String selected = a.selected() == null ? "" : a.selected().trim().toLowerCase();
            String answer = String.valueOf(q.correctOption);
            boolean correct = selected.equalsIgnoreCase(answer);
            if (correct) {
                score++;
            }
            graded.add(new AnswerResult(q.id, selected, correct, answer, q.explanation, q.topic));
        }

        int total = bank.size();
        final Assignment forAssignment = assignment;
        List<QuizResult> prior = results.findByStudentOrderBySubmittedAtDesc(me).stream()
                .filter(r -> r.content.id.equals(content.id)
                        && (forAssignment == null ? r.assignment == null
                        : r.assignment != null && r.assignment.id.equals(forAssignment.id)))
                .toList();
        int attempts = prior.size() + 1;
        int best = prior.stream().mapToInt(r -> r.score).max().orElse(0);
        best = Math.max(best, score);

        results.save(new QuizResult(me, assignment, content, score, total, attempts,
                Math.max(0, req.timeTakenSeconds())));

        return new SubmitResponse(score, total, total == 0 ? 0 : Math.round(score * 100f / total),
                attempts, best, content.title, graded);
    }

    @GetMapping("/results/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String, Object>> myResults() {
        return results.findByStudentOrderBySubmittedAtDesc(currentUser.get()).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.id);
                    row.put("contentId", r.content.id);
                    row.put("contentTitle", r.content.title);
                    row.put("subject", r.content.subject.name());
                    row.put("assignmentId", r.assignment == null ? "none" : r.assignment.id);
                    row.put("score", r.score);
                    row.put("totalQuestions", r.totalQuestions);
                    row.put("percentage", r.percentage());
                    row.put("attempts", r.attempts);
                    row.put("timeTakenSeconds", r.timeTakenSeconds);
                    row.put("submittedAt", r.submittedAt.toString());
                    return row;
                })
                .toList();
    }

    @GetMapping("/assignment/{assignmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, Object> assignmentScores(@PathVariable Long assignmentId) {
        Assignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        User me = currentUser.get();
        if (!assignment.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You did not create this assignment");
        }
        List<Map<String, Object>> rows = members.findByEduClass(assignment.eduClass).stream()
                .map(m -> {
                    List<QuizResult> rs = results.findByAssignmentId(assignment.id).stream()
                            .filter(r -> r.student.id.equals(m.student.id)).toList();
                    QuizResult best = rs.stream().max((x, y) -> Integer.compare(x.score, y.score)).orElse(null);
                    return Map.<String, Object>of(
                            "studentId", m.student.id,
                            "name", m.student.name,
                            "attempts", rs.size(),
                            "bestScore", best == null ? 0 : best.score,
                            "totalQuestions", best == null ? 0 : best.totalQuestions,
                            "percentage", best == null ? 0 : best.percentage());
                }).toList();
        double avg = rows.stream().mapToInt(r -> (int) r.get("percentage")).average().orElse(0);
        return Map.of("assignmentId", assignmentId, "title", assignment.title,
                "classAveragePercentage", Math.round(avg), "students", rows);
    }
}