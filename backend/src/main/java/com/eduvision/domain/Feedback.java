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
 * A developer/user rating (1..5) plus free-text comments on a content item.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "developer_id", nullable = false)
    public User developer;

    /** 1..5 stars. */
    @Column(name = "rating", nullable = false)
    public int rating;

    @Column(name = "comments", length = 2000)
    public String comments;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public Feedback() {
    }

    public Feedback(ArContent content, User developer, int rating, String comments) {
        this.content = content;
        this.developer = developer;
        this.rating = rating;
        this.comments = comments;
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

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public User getDeveloper() {
        return developer;
    }

    public void setDeveloper(User developer) {
        this.developer = developer;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}