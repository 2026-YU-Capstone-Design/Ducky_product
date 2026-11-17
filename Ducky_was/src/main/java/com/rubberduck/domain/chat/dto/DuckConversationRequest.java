package com.rubberduck.domain.chat.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DuckConversationRequest(
        String deviceId,
        String userId,
        @JsonAlias("conversation_id") Long conversationId,
        String inputType,
        String message,
        Map<String, String> learningType
) {
}
