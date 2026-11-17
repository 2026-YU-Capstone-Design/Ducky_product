package com.rubberduck.domain.device.dto;

import com.rubberduck.domain.device.entity.DeviceCommand;

public record DeviceCommandResponse(
        String id,
        String deviceId,
        String deviceSerial,
        Long conversationId,
        String commandType,
        String status,
        String createdAt,
        String claimedAt,
        String completedAt,
        String errorMessage
) {

    public static DeviceCommandResponse from(DeviceCommand command) {
        return new DeviceCommandResponse(
                String.valueOf(command.getId()),
                String.valueOf(command.getDevice().getId()),
                command.getDevice().getSerialNumber(),
                command.getConversationId(),
                command.getCommandType().name(),
                command.getStatus().name(),
                command.getCreatedAt() == null ? null : command.getCreatedAt().toString(),
                command.getClaimedAt() == null ? null : command.getClaimedAt().toString(),
                command.getCompletedAt() == null ? null : command.getCompletedAt().toString(),
                command.getErrorMessage()
        );
    }
}
