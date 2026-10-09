package com.eduvision.web;

import com.eduvision.domain.*;
import com.eduvision.repository.*;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AR content library and the developer authoring workflow.
 *
 * <p>Students only ever see PUBLISHED content, and quiz answers ({@code correctOption}) are stripped
 * from every student-facing payload.
 */
@RestController
@RequestMapping("/api/ar-content")
public class ArContentController {

    private final ArContentRepository contents;
    private final ContentPartRepository parts;
    private final QuizQuestionRepository questions;
    private final FeedbackRepository feedback;
    private final EduClassRepository classes;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public ArContentController(ArContentRepository contents, ContentPartRepository parts,
                               QuizQuestionRepository questions, FeedbackRepository feedback,
                               EduClassRepository classes, UserRepository users,
                               CurrentUser currentUser) {
        this.contents = contents;
        this.parts = parts;
        this.questions = questions;
        this.feedback = feedback;
        this.classes = classes;
        this.users = users;
        this.currentUser = currentUser;
    }

    // -------------------------------------------------------------- payloads

    public record PartDto(Long id, String partName, String label, String explanation, String audioUrl) {
    }

    public record QuestionDto(Long id, String questionText, List<String> options,
                              String correctOption, String explanation, String topic) {
    }

    public record ContentSummary(Long id, String title, Subject subject, String grade, String description,
                                 String modelUrl, String markerId, ContentStatus status, int version,
                                 int partCount, int questionCount, String createdBy) {
    }

    public record ContentDetail(Long id, String title, Subject subject, String grade, String description,
                                String modelUrl, String markerId, String audioUrl, String videoUrl,
                                ContentStatus status, int version, String createdBy,
                                String trackingTestReport, double averageRating,
                                List<PartDto> parts, List<QuestionDto> questions) {
    }

    public record CreateContentRequest(@NotBlank String title, @NotNull Subject subject, String grade,
                                       String description, @NotBlank String modelUrl, String markerId) {
    }

    public record UpdateContentRequest(String title, Subject subject, String grade, String description,
                                       String modelUrl, String markerId, String audioUrl, String videoUrl) {
    }

    public record PartRequest(@NotBlank String partName, String label, String explanation,
                              String audioUrl) {
    }

    public record QuestionRequest(@NotBlank String questionText, @NotBlank String options,
                                  @NotBlank String correctOption, String explanation, String topic) {
    }

    public record FeedbackRequest(int rating, String comments) {
    }

    public record TrackingTestRequest(Integer fps, Integer loadMs, Integer modelLoadMs) {
    }

    // -------------------------------------------------------------- reads

    @GetMapping
    public List<ContentSummary> list() {
        User me = currentUser.get();
        List<ArContent> rows = me.role == Role.STUDENT
                ? contents.findByStatus(ContentStatus.PUBLISHED)
                : (me.role == Role.DEVELOPER ? contents.findByCreatedByOrderByIdAsc(me) : contents.findAll());
        return rows.stream().map(c -> summary(c, me)).toList();
    }

    @GetMapping("/{id}")
    public ContentDetail detail(@PathVariable Long id) {
        User me = currentUser.get();
        ArContent c = require(id);
        boolean staff = me.role != Role.STUDENT;
        List<QuestionDto> qs = questions.findByContentOrderByOrderIndexAsc(c).stream()
                .map(q -> new QuestionDto(q.id, q.questionText, q.optionList(),
                        staff ? String.valueOf(q.correctOption) : null, staff ? q.explanation : null, q.topic))
                .toList();
        return new ContentDetail(c.id, c.title, c.subject, c.grade, c.description, c.modelUrl,
                c.markerId, c.audioUrl, c.videoUrl, c.status, c.version,
                c.createdBy == null ? null : c.createdBy.name,
                c.trackingTestReport, rating(c),
                parts.findByContentOrderByOrderIndexAsc(c).stream()
                        .map(p -> new PartDto(p.id, p.partName, p.label, p.explanation, p.audioUrl)).toList(),
                qs);
    }

    /** Called by the AR scanner once it has decoded a printed marker / QR payload. */
    @GetMapping("/marker/{markerId}")
    public ContentDetail byMarker(@PathVariable String markerId) {
        ArContent c = contents.findByMarkerId(markerId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No AR lesson is registered for marker " + markerId));
        if (c.status != ContentStatus.PUBLISHED && currentUser.get().role == Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That AR lesson is not published yet");
        }
        boolean staff = currentUser.get().role != Role.STUDENT;
        return new ContentDetail(c.id, c.title, c.subject, c.grade, c.description, c.modelUrl,
                c.markerId, c.audioUrl, c.videoUrl, c.status, c.version,
                c.createdBy == null ? null : c.createdBy.name,
                c.trackingTestReport, rating(c),
                parts.findByContentOrderByOrderIndexAsc(c).stream()
                        .map(p -> new PartDto(p.id, p.partName, p.label, p.explanation, p.audioUrl)).toList(),
                questions.findByContentOrderByOrderIndexAsc(c).stream()
                        .map(q -> new QuestionDto(q.id, q.questionText, q.optionList(),
                                staff ? String.valueOf(q.correctOption) : null, staff ? q.explanation : null, q.topic))
                        .toList());
    }

    /** Null-safe: {@code averageRating} returns NULL from SQL when a lesson has no feedback yet. */
    private double rating(ArContent c) {
        Double avg = feedback.averageRating(c);
        return avg == null ? 0.0 : Math.round(avg * 10) / 10.0;
    }

    // -------------------------------------------------------------- developer writes

    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<ContentSummary> create(@Valid @RequestBody CreateContentRequest req) {
        validateModelUrl(req.modelUrl());
        if (req.markerId() != null && !req.markerId().isBlank()
                && contents.existsByMarkerId(req.markerId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That markerId is already in use");
        }
        ArContent c = contents.save(new ArContent(req.title().trim(), req.subject(), req.grade(),
                req.description(), req.modelUrl().trim(),
                req.markerId() == null ? null : req.markerId().trim(),
                ContentStatus.DRAFT, currentUser.get()));
        return ResponseEntity.status(HttpStatus.CREATED).body(summary(c, currentUser.get()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ContentSummary update(@PathVariable Long id, @RequestBody UpdateContentRequest req) {
        ArContent c = requireOwned(id);
        if (req.modelUrl() != null) {
            validateModelUrl(req.modelUrl());
            c.modelUrl = req.modelUrl().trim();
        }
        if (req.title() != null) c.title = req.title().trim();
        if (req.subject() != null) c.subject = req.subject();
        if (req.grade() != null) c.grade = req.grade();
        if (req.description() != null) c.description = req.description();
        if (req.markerId() != null) c.markerId = req.markerId().trim();
        if (req.audioUrl() != null) c.audioUrl = req.audioUrl();
        if (req.videoUrl() != null) c.videoUrl = req.videoUrl();
        c.version++;
        c.updatedAt = Instant.now();
        contents.save(c);
        return summary(c, currentUser.get());
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<PartDto> addPart(@PathVariable Long id, @Valid @RequestBody PartRequest req) {
        ArContent c = requireOwned(id);
        int next = parts.findByContentOrderByOrderIndexAsc(c).size() + 1;
        ContentPart saved = parts.save(new ContentPart(c, req.partName().trim(), req.label(),
                req.explanation(), next));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PartDto(saved.id, saved.partName, saved.label, saved.explanation, saved.audioUrl));
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<QuestionDto> addQuestion(@PathVariable Long id,
                                                   @Valid @RequestBody QuestionRequest req) {
        ArContent c = requireOwned(id);
        List<String> options = new ArrayList<>();
        for (String s : req.options().split("\\|")) {
            if (!s.isBlank()) {
                options.add(s.trim());
            }
        }
        if (options.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Supply at least two options, separated by '|'");
        }
        String correct = req.correctOption().trim().toLowerCase(Locale.ROOT);
        // Accept either the letter key ("b") or the exact option text.
        boolean letterMatch = "abcdefgh".contains(correct) && correct.length() == 1
                && correct.charAt(0) - 'a' < options.size();
        if (!letterMatch && !options.stream().anyMatch(o -> o.equalsIgnoreCase(correct))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "correctOption '" + correct + "' must be an option letter (a-"
                            + (char) ('a' + options.size() - 1) + ") or the exact option text");
        }
        int next = questions.findByContentOrderByOrderIndexAsc(c).size() + 1;
        QuizQuestion saved = questions.save(new QuizQuestion(c, req.questionText().trim(),
                String.join("|", options), correct, req.explanation(), next, req.topic()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new QuestionDto(saved.id, saved.questionText, saved.optionList(),
                        String.valueOf(saved.correctOption), saved.explanation, saved.topic));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('DEVELOPER')")
    public Map<String, Object> publish(@PathVariable Long id) {
        ArContent c = requireOwned(id);
        c.status = ContentStatus.PUBLISHED;
        c.updatedAt = Instant.now();
        contents.save(c);
        return Map.of("id", c.id, "status", c.status.name(), "version", c.version);
    }

    @PostMapping("/{id}/tracking-test")
    @PreAuthorize("hasRole('DEVELOPER')")
    public Map<String, Object> trackingTest(@PathVariable Long id,
                                            @RequestBody TrackingTestRequest req) {
        ArContent c = requireOwned(id);
        c.trackingTestReport = "fps=" + req.fps() + ", loadMs=" + req.loadMs()
                + ", modelLoadMs=" + req.modelLoadMs();
        c.updatedAt = Instant.now();
        contents.save(c);
        return Map.of("id", c.id, "report", c.trackingTestReport, "result",
                (req.fps() != null && req.fps() >= 30) ? "PASS" : "NEEDS_OPTIMISATION");
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('DEVELOPER','TEACHER')")
    public ResponseEntity<Feedback> addFeedback(@PathVariable Long id,
                                               @RequestBody FeedbackRequest req) {
        ArContent c = require(id);
        int rating = Math.clamp(req.rating(), 1, 5);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedback.save(new Feedback(c, currentUser.get(), rating, req.comments())));
    }

    @GetMapping("/{id}/feedback")
    public List<Map<String, Object>> listFeedback(@PathVariable Long id) {
        ArContent c = require(id);
        return feedback.findByContentOrderByIdDesc(c).stream()
                .map(f -> Map.<String, Object>of("rating", f.rating, "comments",
                        String.valueOf(f.comments), "by",
                        f.developer == null ? "unknown" : f.developer.name))
                .toList();
    }

    // -------------------------------------------------------------- helpers

    private ArContent require(Long id) {
        return contents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AR content not found"));
    }

    private ArContent requireOwned(Long id) {
        ArContent c = require(id);
        User me = currentUser.get();
        if (c.createdBy == null || !c.createdBy.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own AR content");
        }
        return c;
    }

    /** AR content must reference a real online glTF binary asset. */
    private void validateModelUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelUrl is required");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "modelUrl must be an http(s) URL so the AR viewer can fetch the 3D model");
        }
        if (!(lower.endsWith(".glb") || lower.endsWith(".gltf"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelUrl must point to a .glb or .gltf asset");
        }
    }

    private ContentSummary summary(ArContent c, User me) {
        return new ContentSummary(c.id, c.title, c.subject, c.grade, c.description, c.modelUrl,
                c.markerId, c.status, c.version,
                Math.toIntExact(parts.countByContent(c)),
                Math.toIntExact(questions.countByContent(c)),
                c.createdBy == null ? null : c.createdBy.name);
    }
}