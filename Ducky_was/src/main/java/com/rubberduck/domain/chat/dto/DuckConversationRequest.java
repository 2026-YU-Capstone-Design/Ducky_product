package com.rubberduck.domain.chat.dto;

import java.util.Map;

public record DuckConversationRequest(
        String deviceId,
        String userId,
        String inputType,
        String message,
        Map<String, String> learningType
) {
}
