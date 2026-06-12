package com.rubberduck.domain.chat.dto;

public record DuckConversationResponse(
        Long conversationId,
        String responseType,
        String message,
        Integer hintLevel,
        boolean shouldSaveLog
) {
}
