package com.eduvision.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A platform account: teacher, student or content developer.
 * Passwords are never stored plain text - see com.eduvision.util.PasswordUtil.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false, length = 120)
    public String name;

    @Column(name = "email", nullable = false, unique = true, length = 180)
    public String email;

    /** PBKDF2 hash produced by PasswordUtil.hash(...). Never plain text. */
    @Column(name = "password_hash", nullable = false, length = 300)
    /**
     * PBKDF2 hash. NEVER serialize it: several endpoints return entities that embed a {@code User}
     * (feedback, live participants), so this field is explicitly excluded from all JSON output.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    public Role role;

    @Column(name = "grade", length = 40)
    public String grade;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public User() {
    }

    public User(String name, String email, String passwordHash, Role role, String grade) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.grade = grade;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}