package com.eduvision.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Per-student tracking of one content item, optionally scoped to an assignment.
 * Unique on (student, content, assignment).
 */
@Entity
@Table(name = "student_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_student_content_assignment",
                columnNames = {"student_id", "content_id", "assignment_id"}))
public class Progress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    public User student;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    /** Optional - null when the student explores content freely, outside any assignment. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id")
    public Assignment assignment;

    @Column(name = "time_spent_seconds", nullable = false)
    public long timeSpentSeconds;

    /** 0..100 percent. */
    @Column(name = "completion", nullable = false)
    public int completion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public ProgressStatus status;

    @Column(name = "session_count", nullable = false)
    public int sessionCount;

    @Column(name = "last_accessed", nullable = false)
    public Instant lastAccessed;

    public Progress() {
    }

    public Progress(User student, ArContent content, Assignment assignment) {
        this.student = student;
        this.content = content;
        this.assignment = assignment;
        this.status = ProgressStatus.NOT_STARTED;
        this.lastAccessed = Instant.now();
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

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }

    public long getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(long timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public int getCompletion() {
        return completion;
    }

    public void setCompletion(int completion) {
        this.completion = completion;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public Instant getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Instant lastAccessed) {
        this.lastAccessed = lastAccessed;
    }
}