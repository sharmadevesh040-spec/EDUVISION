package com.eduvision.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** One student's presence inside a live classroom session. */
@Entity
@Table(name = "live_participants",
       uniqueConstraints = @UniqueConstraint(name = "uk_live_session_student",
               columnNames = {"session_id", "student_id"}))
public class LiveParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false)
    public LiveSession session;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    public User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ProgressStatus status = ProgressStatus.NOT_STARTED;

    @Column(nullable = false)
    public int completion;

    @Column(name = "joined_at", nullable = false)
    public Instant joinedAt = Instant.now();

    @Column(name = "last_heartbeat", nullable = false)
    public Instant lastHeartbeat = Instant.now();

    public LiveParticipant() {
    }

    public LiveParticipant(LiveSession session, User student) {
        this.session = session;
        this.student = student;
        this.joinedAt = Instant.now();
        this.lastHeartbeat = Instant.now();
    }
}