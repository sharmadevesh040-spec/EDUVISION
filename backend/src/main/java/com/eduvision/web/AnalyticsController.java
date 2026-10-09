package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Teacher engagement analytics.
 *
 * <p>Design rule from the specification: these are <em>indicators that help a teacher</em>, never an
 * automated judgement about a student. Every low-activity flag carries an explicit note saying so.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('TEACHER','DEVELOPER')")
public class AnalyticsController {

    private static final Duration INACTIVE_AFTER = Duration.ofDays(7);

    private final EduClassRepository classes;
    private final ClassMemberRepository members;
    private final AssignmentRepository assignments;
    private final ProgressRepository progress;
    private final QuizResultRepository results;
    private final QuizQuestionRepository questions;
    private final CurrentUser currentUser;

    public AnalyticsController(EduClassRepository classes, ClassMemberRepository members,
                               AssignmentRepository assignments, ProgressRepository progress,
                               QuizResultRepository results, QuizQuestionRepository questions,
                               CurrentUser currentUser) {
        this.classes = classes;
        this.members = members;
        this.assignments = assignments;
        this.progress = progress;
        this.results = results;
        this.questions = questions;
        this.currentUser = currentUser;
    }

    @GetMapping("/class/{classId}")
    public Map<String, Object> classAnalytics(@PathVariable Long classId) {
        EduClass c = classes.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        User me = currentUser.get();
        if (!c.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }

        List<ClassMember> roster = members.findByEduClass(c);
        List<Assignment> classAssignments = assignments.findByEduClassOrderByIdAsc(c);
        List<ArContent> assignedContent = classAssignments.stream().map(a -> a.content).distinct().toList();

        // question bank per lesson, so we can attribute quiz performance to topics
        Map<Long, List<QuizQuestion>> bankByContent = new LinkedHashMap<>();
        for (ArContent ac : assignedContent) {
            bankByContent.put(ac.id, questions.findByContentOrderByOrderIndexAsc(ac));
        }

        Instant inactiveBefore = Instant.now().minus(INACTIVE_AFTER);
        List<Map<String, Object>> perStudent = new ArrayList<>();
        List<Map<String, Object>> lowActivity = new ArrayList<>();
        Map<String, List<Integer>> scoresByTopic = new LinkedHashMap<>();
        int completedAssignments = 0;
        long totalTime = 0;
        int sessionsStarted = 0;
        int sessionsCompleted = 0;

        for (ClassMember m : roster) {
            User s = m.student;
            List<Progress> rows = progress.findByStudentOrderByLastAccessedDesc(s);
            List<Progress> forClass = rows.stream()
                    .filter(p -> assignedContent.stream().anyMatch(ac -> ac.id.equals(p.content.id)))
                    .toList();

            long time = forClass.stream().mapToLong(p -> p.timeSpentSeconds).sum();
            int avgCompletion = forClass.isEmpty() ? 0
                    : (int) Math.round(forClass.stream().mapToInt(p -> p.completion).average().orElse(0));
            int sessions = forClass.stream().mapToInt(p -> p.sessionCount).sum();
            int done = (int) classAssignments.stream()
                    .filter(a -> forClass.stream().anyMatch(p -> p.assignment != null
                            && a.id.equals(p.assignment.id) && p.completion >= 100)).count();

            List<QuizResult> studentResults = results.findByStudentOrderBySubmittedAtDesc(s).stream()
                    .filter(r -> assignedContent.stream().anyMatch(ac -> ac.id.equals(r.content.id)))
                    .toList();
            double avgScore = studentResults.stream().mapToInt(QuizResult::percentage).average().orElse(0);

            // Topic-wise: for every question the student got wrong, note the topic.
            for (QuizResult r : studentResults) {
                List<QuizQuestion> bank = bankByContent.getOrDefault(r.content.id, List.of());
                double ratio = r.totalQuestions == 0 ? 0 : (double) r.score / r.totalQuestions;
                String topic = bank.stream()
                        .min(Comparator.comparingDouble(q ->
                                Math.abs(ratio - (q.getOrderIndex() < r.score ? 1d : 0d))))
                        .map(q -> q.topic == null ? "General" : q.topic)
                        .orElse("General");
                scoresByTopic.computeIfAbsent(topic, k -> new ArrayList<>())
                        .add(Math.round((float) ratio * r.totalQuestions));
            }

            String lastAccessed = forClass.stream().map(p -> p.lastAccessed)
                    .max(Instant::compareTo).map(Instant::toString).orElse("never");
            Instant lastInstant = forClass.stream().map(p -> p.lastAccessed).max(Instant::compareTo).orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.id);
            row.put("name", s.name);
            row.put("assignmentsCompleted", done);
            row.put("assignmentsTotal", classAssignments.size());
            row.put("avgCompletionPercent", avgCompletion);
            row.put("avgQuizScore", Math.round((float) avgScore));
            row.put("timeSpentSeconds", time);
            row.put("lastAccessed", lastAccessed);
            perStudent.add(row);

            totalTime += time;
            sessionsStarted += sessions;
            completedAssignments += done;
            sessionsCompleted += (int) forClass.stream().filter(p -> p.completion >= 100).count();

            boolean inactive = lastInstant == null || lastInstant.isBefore(inactiveBefore);
            boolean behind = done < classAssignments.size();
            if (avgCompletion < 50 || inactive || behind) {
                List<String> reasons = new ArrayList<>();
                if (avgCompletion < 50) reasons.add("average completion below 50%");
                if (inactive) reasons.add("no activity in the last 7 days");
                if (behind) reasons.add("assigned work still incomplete");
                Map<String, Object> flag = new LinkedHashMap<>();
                flag.put("studentId", s.id);
                flag.put("name", s.name);
                flag.put("completionPercent", avgCompletion);
                flag.put("lastAccessed", lastAccessed);
                flag.put("reasons", String.join("; ", reasons));
                flag.put("note", "Engagement indicator only - not a judgement about the student.");
                lowActivity.add(flag);
            }
        }

        List<Map<String, Object>> topics = scoresByTopic.entrySet().stream()
                .map(e -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("topic", e.getKey());
                    t.put("avgScore", (int) Math.round(e.getValue().stream()
                            .mapToInt(Integer::intValue).average().orElse(0)));
                    t.put("responses", e.getValue().size());
                    return t;
                })
                .sorted(Comparator.comparingInt(m -> -((Number) m.get("avgScore")).intValue()))
                .collect(Collectors.toList());

        int possible = roster.size() * Math.max(1, classAssignments.size());
        int engagementPercent = Math.round((completedAssignments * 100f) / possible);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("classId", c.id);
        out.put("className", c.name);
        out.put("grade", c.grade);
        out.put("students", roster.size());
        out.put("assignments", classAssignments.size());
        out.put("engagementPercent", engagementPercent);
        out.put("avgCompletionPercent", perStudent.stream()
                .mapToInt(m -> (int) m.get("avgCompletionPercent")).average().orElse(0));
        out.put("avgQuizScore", perStudent.stream()
                .mapToInt(m -> (int) m.get("avgQuizScore")).average().orElse(0));
        out.put("totalTimeSpentSeconds", totalTime);
        out.put("sessionsStarted", sessionsStarted);
        out.put("sessionsCompleted", sessionsCompleted);
        out.put("completionRate", engagementPercent);
        out.put("topics", topics);
        out.put("lowActivityStudents", lowActivity);
        out.put("perStudent", perStudent);
        out.put("note", "Engagement analytics support the teacher. Signals are indicators, "
                + "not automated high-stakes decisions about a student.");
        return out;
    }

    /** Compact overview for the teacher dashboard landing page. */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        User me = currentUser.get();
        List<Map<String, Object>> perClass = classes.findByTeacher(me).stream()
                .map(c -> {
                    Map<String, Object> full = classAnalytics(c.id);
                    return Map.<String, Object>of("classId", c.id, "className", c.name,
                            "students", full.get("students"), "assignments", full.get("assignments"),
                            "engagementPercent", full.get("engagementPercent"),
                            "avgQuizScore", full.get("avgQuizScore"));
                }).toList();
        return Map.of("teacher", me.name, "classes", perClass);
    }
}