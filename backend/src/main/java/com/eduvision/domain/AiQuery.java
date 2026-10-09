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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A stored AI Tutor exchange, so the tutor history is inspectable and testable.
 * Both student and content are optional - a student may ask a general question.
 */
@Entity
@Table(name = "ai_queries")
public class AiQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id")
    public User student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "content_id")
    public ArContent content;

    @Column(name = "question_text", nullable = false, length = 1000)
    public String questionText;

    @Column(name = "answer_text", length = 4000)
    public String answerText;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public AiQuery() {
    }

    public AiQuery(User student, ArContent content, String questionText, String answerText) {
        this.student = student;
        this.content = content;
        this.questionText = questionText;
        this.answerText = answerText;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}