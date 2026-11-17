package com.rubberduck.domain.device.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_command_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 40)
    private DeviceCommandType commandType = DeviceCommandType.START_RECORDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeviceCommandStatus status = DeviceCommandStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public static DeviceCommand startRecording(Device device, Long conversationId) {
        DeviceCommand command = new DeviceCommand();
        command.setDevice(device);
        command.setConversationId(conversationId);
        command.setCommandType(DeviceCommandType.START_RECORDING);
        command.setStatus(DeviceCommandStatus.PENDING);
        return command;
    }

    public void claim() {
        status = DeviceCommandStatus.CLAIMED;
        claimedAt = Instant.now();
    }

    public void complete(boolean success, String errorMessage) {
        status = success ? DeviceCommandStatus.COMPLETED : DeviceCommandStatus.FAILED;
        completedAt = Instant.now();
        this.errorMessage = success ? null : truncate(errorMessage, 1000);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
