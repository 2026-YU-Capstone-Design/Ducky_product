package com.rubberduck.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record StartDeviceCommandRequest(
        @JsonAlias("conversation_id") Long conversationId
) {
}
