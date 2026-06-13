package com.rubberduck.domain.iot.dto;

import com.rubberduck.domain.device.entity.DeviceCommand;

public record IotNextCommandResponse(
        boolean available,
        Long commandId,
        String commandType,
        Long conversationId
) {

    public static IotNextCommandResponse none() {
        return new IotNextCommandResponse(false, null, null, null);
    }

    public static IotNextCommandResponse from(DeviceCommand command) {
        return new IotNextCommandResponse(
                true,
                command.getId(),
                command.getCommandType().name(),
                command.getConversationId()
        );
    }
}
