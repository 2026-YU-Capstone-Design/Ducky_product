package com.rubberduck.domain.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "login_id", nullable = false, unique = true, length = 80)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String level = "beginner";

    @Column(name = "processing_style", nullable = false, length = 30)
    private String processingStyle = "active";

    @Column(name = "expression_style", nullable = false, length = 30)
    private String expressionStyle = "visual";

    @Column(name = "understanding_style", nullable = false, length = 30)
    private String understandingStyle = "sequential";

    @Column(nullable = false)
    private boolean onboarded = false;

    @Column(name = "streak_days", nullable = false)
    private int streakDays = 0;

    @Column(name = "completed_session_count", nullable = false)
    private int completedSessionCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User create(String name, String email, String loginId, String passwordHash) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setLoginId(loginId);
        user.setPasswordHash(passwordHash);
        return user;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
