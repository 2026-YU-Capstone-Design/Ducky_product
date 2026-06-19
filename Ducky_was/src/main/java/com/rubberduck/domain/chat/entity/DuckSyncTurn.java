package com.rubberduck.domain.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "duck_sync_turns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"device_serial", "client_turn_id"})
)
public class DuckSyncTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_serial", nullable = false, length = 120)
    private String deviceSerial;

    @Column(name = "client_turn_id", nullable = false, length = 80)
    private String clientTurnId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DuckSyncTurn create(String deviceSerial, String clientTurnId, Long conversationId) {
        DuckSyncTurn turn = new DuckSyncTurn();
        turn.setDeviceSerial(deviceSerial);
        turn.setClientTurnId(clientTurnId);
        turn.setConversationId(conversationId);
        return turn;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
