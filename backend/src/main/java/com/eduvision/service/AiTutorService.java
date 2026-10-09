package com.eduvision.service;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.ContentPart;
import com.eduvision.repository.AiQueryRepository;
import com.eduvision.repository.ArContentRepository;
import com.eduvision.repository.ContentPartRepository;
import com.eduvision.repository.QuizQuestionRepository;
import com.eduvision.repository.UserRepository;
import com.eduvision.domain.AiQuery;
import com.eduvision.domain.QuizQuestion;
import com.eduvision.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Tutor for AR EduVision.
 *
 * <p>Two design rules from the specification are enforced here:
 * <ol>
 *   <li>Answers are <b>grounded in teacher/developer-approved content</b>. The prompt context is built
 *       only from the linked lesson's description, its hotspot explanations and its quiz answers -
 *       the model never gets free-rein on school material.</li>
 *   <li>The tutor must work <b>with no external AI provider configured</b>, because a classroom
 *       demo must never fail. When {@code eduvision.ai.api-key} is absent (or the HTTP call fails)
 *       an offline retrieval + template engine answers from the approved content instead.</li>
 * </ol>
 * Every question/answer pair is logged so it can be audited.
 */
@Service
public class AiTutorService {

    private static final Logger log = LoggerFactory.getLogger(AiTutorService.class);

    private final ArContentRepository contents;
    private final ContentPartRepository parts;
    private final QuizQuestionRepository questions;
    private final UserRepository users;
    private final AiQueryRepository aiQueries;

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public AiTutorService(ArContentRepository contents, ContentPartRepository parts,
                          QuizQuestionRepository questions, UserRepository users,
                          AiQueryRepository aiQueries,
                          @Value("${eduvision.ai.api-key:}") String apiKey,
                          @Value("${eduvision.ai.url:https://api.openai.com/v1/chat/completions}") String apiUrl,
                          @Value("${eduvision.ai.model:gpt-4o-mini}") String model) {
        this.contents = contents;
        this.parts = parts;
        this.questions = questions;
        this.users = users;
        this.aiQueries = aiQueries;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = apiUrl;
        this.model = model;
    }

    public record TutorAnswer(String answer, String source, String relatedContentTitle, String engine) {
    }

    public TutorAnswer ask(String email, Long contentId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        ArContent content = contentId == null ? null : contents.findById(contentId).orElse(null);
        if (content == null && contentId != null) {
            throw new IllegalArgumentException("Unknown AR content id " + contentId);
        }

        String approved = approvedContext(content);
        String answer = apiKey.isEmpty()
                ? offlineAnswer(question, content, approved)
                : remoteAnswer(question, content, approved).orElseGet(() -> offlineAnswer(question, content, approved));

        User student = email == null ? null
                : users.findByEmailIgnoreCase(email).orElse(null);
        aiQueries.save(new AiQuery(student, content, question, answer));

        return new TutorAnswer(answer, "curriculum-grounded",
                content == null ? "General science" : content.title,
                apiKey.isEmpty() ? "offline-grounded" : "llm-grounded");
    }

    /** Builds the ONLY context the tutor is allowed to use. */
    public String approvedContext(ArContent content) {
        if (content == null) {
            return "No specific AR lesson selected. General scientific reasoning only.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AR lesson: ").append(content.title)
                .append(" (subject ").append(content.subject).append(", grade ").append(content.grade).append(")\n");
        sb.append("Approved description: ").append(content.description == null ? "n/a" : content.description).append("\n");
        sb.append("Approved labelled parts:\n");
        for (ContentPart p : parts.findByContentOrderByOrderIndexAsc(content)) {
            sb.append("- ").append(p.label == null ? p.partName : p.label)
                    .append(": ").append(p.explanation == null ? "" : p.explanation).append("\n");
        }
        sb.append("Approved quiz knowledge:\n");
        for (QuizQuestion q : questions.findByContentOrderByOrderIndexAsc(content)) {
            sb.append("- Q: ").append(q.questionText)
                    .append(" | Answer: ").append(q.correctOption)
                    .append(" | Why: ").append(q.explanation == null ? "" : q.explanation).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ offline engine

    String offlineAnswer(String question, ArContent content, String approved) {
        List<ScoredSentence> candidates = sentences(approved);
        String q = question.toLowerCase(Locale.ROOT);

        // Give a hint rather than the answer when the question mentions one of the approved labels.
        boolean hint = q.contains("hint") || q.contains("tell me the answer") || q.contains("just give me");
        if (hint) {
            ScoredSentence best = bestMatch(candidates, q).orElse(null);
            return (best == null
                    ? "Try rephrasing your question using words from the lesson labels."
                    : "Hint: revisit the explanation for \"" + (best.label == null ? best.text : best.label)
                    + "\" in this AR lesson, then answer in your own words. "
                    + "I will not give you the answer directly.")
                    + "\n\n(Answering from teacher-approved AR content only.)";
        }

        return bestMatch(candidates, q)
                .map(s -> (content == null
                        ? "From the approved lesson material: " + s.text
                        : "Based on the AR lesson \"" + content.title + "\": " + s.text)
                        + "\n\n(Source: " + (content == null ? "general" : content.title)
                        + " - teacher/developer-approved content.)")
                .orElseGet(() -> {
                    String approvedTopics = approved.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                    StringBuilder sb = new StringBuilder();
                    sb.append("I could not find that specific point in the approved AR lesson material, so I "
                            + "will not invent an answer. Try asking about one of these approved topics:\n");
                    List<String> labels = labelsOf(approved).limit(6).toList();
                    if (labels.isEmpty()) {
                        sb.append("- the lesson description\n");
                    } else {
                        labels.forEach(l -> sb.append("- ").append(l).append('\n'));
                    }
                    sb.append("\n(Grounding check: your question matched ").append(approvedTopics.length())
                            .append(" characters of approved content.)");
                    return sb.toString();
                });
    }

    private record ScoredSentence(String label, String text, Set<String> keywords) {
    }

    private List<ScoredSentence> sentences(String approved) {
        List<ScoredSentence> out = new ArrayList<>();
        for (String line : approved.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 25) continue;
            String label;
            String body = trimmed;
            int colon = trimmed.indexOf(": ");
            if (trimmed.startsWith("- ") && colon > 0) {
                label = trimmed.substring(2, colon).trim();
                body = trimmed.substring(colon + 2).trim();
            } else {
                label = null;
            }
            out.add(new ScoredSentence(label, body, keywords(body)));
        }
        return out;
    }

    private Optional<ScoredSentence> bestMatch(List<ScoredSentence> candidates, String question) {
        Set<String> q = keywords(question);
        return candidates.stream()
                .max(Comparator.comparingDouble(c -> {
                    if (q.isEmpty() || c.keywords().isEmpty()) return 0;
                    long hits = c.keywords().stream().filter(q::contains).count();
                    return hits / (double) q.size();
                }))
                .filter(c -> {
                    if (q.isEmpty() || c.keywords().isEmpty()) return false;
                    return c.keywords().stream().anyMatch(q::contains);
                });
    }

    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "of", "to", "in", "on", "for", "and",
            "or", "why", "what", "how", "does", "do", "it", "this", "that", "with", "as", "at", "by",
            "from", "can", "you", "me", "i", "we", "they", "he", "she", "his", "her", "its", "about",
            "explain", "tell", "give", "part", "model", "lesson", "ar");

    private Set<String> keywords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() > 3 && !STOP.contains(w))
                .collect(Collectors.toSet());
    }

    private java.util.stream.Stream<String> labelsOf(String approved) {
        return java.util.stream.Stream.of(approved.split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("- "))
                .map(l -> l.substring(2).split(": ")[0])
                .map(l -> l.replaceFirst("^Q:\\s*", ""))
                .filter(s -> !s.isBlank());
    }

    // ------------------------------------------------------------------ optional LLM

    private Optional<String> remoteAnswer(String question, ArContent content, String approved) {
        try {
            String system = "You are the AR EduVision tutor for a school classroom. Answer ONLY from the "
                    + "approved AR lesson material supplied by the teacher or content developer. Be concise, "
                    + "grade-appropriate, and never invent facts that are not in that material. If the "
                    + "material does not cover the question, say so.\n\nAPPROVED MATERIAL:\n" + approved;
            String body = """
                    {"model":"%s","temperature":0.2,"messages":[
                      {"role":"system","content":%s},
                      {"role":"user","content":%s}]}
                    """.formatted(model, jsonString(system), jsonString(question));

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("AI provider returned {} - falling back to offline grounded answer",
                        response.statusCode());
                return Optional.empty();
            }
            String text = extractContent(response.body());
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("AI provider call failed ({}: {}) - falling back to offline grounded answer",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    /** Minimal extraction so we do not need a JSON dependency. */
    private String extractContent(String json) {
        String key = "\"content\":";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int start = json.indexOf('"', i + key.length());
        if (start < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int j = start + 1; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(++j);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'u' -> { j += 4; sb.append(' '); yield ' '; }
                    default -> n;
                });
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    // ------------------------------------------------------------------ question generation

    /** Generates practice questions FROM the approved content only (teacher-approved grounding). */
    public List<Map<String, Object>> generatePracticeQuestions(Long contentId, int count, String email) {
        ArContent content = contents.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown AR content id " + contentId));
        String approved = approvedContext(content);
        List<Map<String, Object>> out = new ArrayList<>();

        for (ScoredSentence s : sentences(approved)) {
            if (out.size() >= count) break;
            if (s.label() == null) continue;
            String body = firstSentences(s.text(), 2);
            if (body.length() < 40) continue;
            out.add(new LinkedHashMap<>(Map.of(
                    "question", "In the AR lesson \"" + content.title + "\", what should you remember about "
                            + s.label().toLowerCase(Locale.ROOT) + "?",
                    "answer", body,
                    "topic", content.subject.name(),
                    "source", "teacher-approved content: " + s.label(),
                    "generated", true)));
        }
        if (out.isEmpty()) {
            out.add(Map.of(
                    "question", "Summarise the key idea of the AR lesson \"" + content.title + "\".",
                    "answer", truncate(content.description, 240),
                    "topic", content.subject.name(),
                    "source", "teacher-approved content: lesson description",
                    "generated", true));
        }
        if (email != null) {
            users.findByEmailIgnoreCase(email).ifPresent(u ->
                    aiQueries.save(new AiQuery(u, content, "generate-practice-questions",
                            "Generated " + out.size() + " practice questions from approved content.")));
        }
        return out;
    }

    private String firstSentences(String text, int n) {
        String[] parts = text.split("(?<=[.!?])\\s+");
        return truncate(Arrays.stream(parts).limit(n).collect(Collectors.joining(" ")), 300);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}