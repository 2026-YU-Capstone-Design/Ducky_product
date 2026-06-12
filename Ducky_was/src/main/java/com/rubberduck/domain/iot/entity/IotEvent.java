package com.rubberduck.domain.iot.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "iot_events")
public class IotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iot_event_id")
    private Long id;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "user_id", length = 120)
    private String userId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "current_state", length = 60)
    private String currentState;

    @Column(name = "stt_text", length = 4000)
    private String sttText;

    @Column(name = "user_message", length = 4000)
    private String userMessage;

    @Column(name = "assistant_message", length = 4000)
    private String assistantMessage;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "stt_success")
    private Boolean sttSuccess;

    @Column(name = "tts_success")
    private Boolean ttsSuccess;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static IotEvent create(String eventType, String deviceId) {
        IotEvent event = new IotEvent();
        event.setEventType(eventType);
        event.setDeviceId(deviceId);
        return event;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
