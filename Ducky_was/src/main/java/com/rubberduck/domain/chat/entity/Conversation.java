package com.rubberduck.domain.chat.entity;

import java.time.Instant;

import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "persona_id")
    private Long personaId;

    @Column(nullable = false, length = 160)
    private String title = "Ducky learning session";

    @Column(nullable = false, length = 120)
    private String topic = "러버덕 질문 훈련";

    @Column(nullable = false, length = 40)
    private String status = "in_progress";

    @Column(length = 600)
    private String summary = "진행 중인 러버덕 학습 세션입니다.";

    @Column(name = "hint_count", nullable = false)
    private int hintCount = 0;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Conversation start(User user, Device device, Long personaId, String title, String topic) {
        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setDevice(device);
        conversation.setPersonaId(personaId);
        if (title != null && !title.isBlank()) {
            conversation.setTitle(title.trim());
        }
        if (topic != null && !topic.isBlank()) {
            conversation.setTopic(topic.trim());
        }
        return conversation;
    }

    public void increaseMessageCount() {
        messageCount++;
    }

    public void increaseHintCount() {
        hintCount++;
    }

    public void complete() {
        status = "completed";
        completedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        startedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
