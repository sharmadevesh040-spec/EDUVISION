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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * An AR lesson item: one online 3D model plus its teaching metadata.
 * {@code modelUrl} points at a real .glb hosted on the public internet.
 */
@Entity
@Table(name = "ar_contents")
public class ArContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "title", nullable = false, length = 200)
    public String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false, length = 30)
    public Subject subject;

    @Column(name = "grade", length = 40)
    public String grade;

    @Column(name = "description", length = 4000)
    public String description;

    /** Online .glb URL, e.g. https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/DamagedHelmet/glTF-Binary/DamagedHelmet.glb */
    @Column(name = "model_url", nullable = false, length = 600)
    public String modelUrl;

    /** QR / marker payload that resolves to this content in AR. */
    @Column(name = "marker_id", nullable = false, unique = true, length = 80)
    public String markerId;

    @Column(name = "audio_url", length = 600)
    public String audioUrl;

    @Column(name = "video_url", length = 600)
    public String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public ContentStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id")
    public User createdBy;

    /** Content revision number, bumped on every publish. */
    @Column(name = "version", nullable = false)
    public int version;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** Result of the developer's on-device tracking performance test. */
    @Column(name = "tracking_test_report", length = 1000)
    public String trackingTestReport;

    public ArContent() {
    }

    public ArContent(String title, Subject subject, String grade, String description,
                     String modelUrl, String markerId, ContentStatus status, User createdBy) {
        this.title = title;
        this.subject = subject;
        this.grade = grade;
        this.description = description;
        this.modelUrl = modelUrl;
        this.markerId = markerId;
        this.status = status;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = createdAt;
        if (version == 0) {
            version = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public String getMarkerId() {
        return markerId;
    }

    public void setMarkerId(String markerId) {
        this.markerId = markerId;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public void setStatus(ContentStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}