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
import jakarta.persistence.UniqueConstraint;

/**
 * Enrolment link between a student and an {@link EduClass}.
 * A student appears at most once per class.
 */
@Entity
@Table(name = "class_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_student",
                columnNames = {"edu_class_id", "student_id"}))
public class ClassMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "edu_class_id", nullable = false)
    public EduClass eduClass;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    public User student;

    @Column(name = "joined_at", nullable = false)
    public Instant joinedAt;

    public ClassMember() {
    }

    public ClassMember(EduClass eduClass, User student) {
        this.eduClass = eduClass;
        this.student = student;
        this.joinedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EduClass getEduClass() {
        return eduClass;
    }

    public void setEduClass(EduClass eduClass) {
        this.eduClass = eduClass;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}