package com.eduvision.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Live classroom mode: teacher opens a session, students join with a code and heartbeat. */
@Entity
@Table(name = "live_sessions")
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "join_code", nullable = false, unique = true)
    public String joinCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_id", nullable = false)
    public EduClass eduClass;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id")
    public Assignment assignment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    public User teacher;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    public Instant endedAt;

    public LiveSession() {
    }

    public LiveSession(String joinCode, EduClass eduClass, Assignment assignment, User teacher) {
        this.joinCode = joinCode;
        this.eduClass = eduClass;
        this.assignment = assignment;
        this.teacher = teacher;
        this.active = true;
        this.startedAt = Instant.now();
    }
}