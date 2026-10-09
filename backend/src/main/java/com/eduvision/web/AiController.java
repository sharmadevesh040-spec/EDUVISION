package com.eduvision.web;

import com.eduvision.security.CurrentUser;
import com.eduvision.service.AiTutorService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** AI Tutor endpoints. Answers are grounded in teacher-approved AR content only. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiTutorService tutor;
    private final CurrentUser currentUser;

    public AiController(AiTutorService tutor, CurrentUser currentUser) {
        this.tutor = tutor;
        this.currentUser = currentUser;
    }

    public record AskRequest(Long contentId, @NotBlank String question) {
    }

    public record GenerateRequest(@NotNull Long contentId, @Min(1) @Max(10) int count) {
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','DEVELOPER')")
    public AiTutorService.TutorAnswer ask(@RequestBody AskRequest req) {
        return tutor.ask(currentUser.get().email, req.contentId(), req.question().trim());
    }

    @PostMapping("/generate-questions")
    @PreAuthorize("hasRole('TEACHER')")
    public Map<String, Object> generate(@RequestBody GenerateRequest req) {
        List<Map<String, Object>> questions =
                tutor.generatePracticeQuestions(req.contentId(), req.count(), currentUser.get().email);
        return Map.of("contentId", req.contentId(), "count", questions.size(),
                "groundedIn", "teacher-approved AR content", "questions", questions);
    }
}