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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A graded task: a teacher assigns an {@link ArContent} to an {@link EduClass}.
 * Table name is explicit because ASSIGNMENT is a reserved SQL word.
 */
@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "title", nullable = false, length = 200)
    public String title;

    @Column(name = "instructions", length = 4000)
    public String instructions;

    /** Optional - null means "no deadline". */
    @Column(name = "deadline")
    public Instant deadline;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "edu_class_id", nullable = false)
    public EduClass eduClass;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    public User teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public AssignmentStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public Assignment() {
    }

    public Assignment(String title, String instructions, Instant deadline,
                      EduClass eduClass, ArContent content, User teacher) {
        this.title = title;
        this.instructions = instructions;
        this.deadline = deadline;
        this.eduClass = eduClass;
        this.content = content;
        this.teacher = teacher;
        this.status = AssignmentStatus.NOT_STARTED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = AssignmentStatus.NOT_STARTED;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public EduClass getEduClass() {
        return eduClass;
    }

    public void setEduClass(EduClass eduClass) {
        this.eduClass = eduClass;
    }

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}