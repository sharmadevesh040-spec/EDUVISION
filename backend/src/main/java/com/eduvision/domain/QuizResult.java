package com.eduvision.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A submitted quiz attempt: score out of totalQuestions, plus timing.
 */
@Entity
@Table(name = "quiz_results")
public class QuizResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    public User student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id")
    public Assignment assignment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    @Column(name = "score", nullable = false)
    public int score;

    @Column(name = "total_questions", nullable = false)
    public int totalQuestions;

    @Column(name = "attempts", nullable = false)
    public int attempts;

    @Column(name = "submitted_at", nullable = false)
    public Instant submittedAt;

    @Column(name = "time_taken_seconds", nullable = false)
    public long timeTakenSeconds;

    public QuizResult() {
    }

    public QuizResult(User student, Assignment assignment, ArContent content,
                      int score, int totalQuestions, int attempts, long timeTakenSeconds) {
        this.student = student;
        this.assignment = assignment;
        this.content = content;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.attempts = attempts;
        this.timeTakenSeconds = timeTakenSeconds;
        this.submittedAt = Instant.now();
    }

    /** Percentage 0..100, 0 when there are no questions. */
    public int percentage() {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((score * 100.0) / totalQuestions);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public long getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public void setTimeTakenSeconds(long timeTakenSeconds) {
        this.timeTakenSeconds = timeTakenSeconds;
    }
}