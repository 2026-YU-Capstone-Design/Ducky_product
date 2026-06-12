package com.rubberduck.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record StartConversationRequest(
        @JsonAlias("device_id") Long deviceId,
        @JsonAlias("persona_id") Long personaId,
        String title,
        String topic
) {
}
