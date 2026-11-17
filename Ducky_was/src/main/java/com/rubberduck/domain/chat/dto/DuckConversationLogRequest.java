package com.rubberduck.domain.chat.dto;

public record DuckConversationLogRequest(
        Long conversationId,
        String deviceId,
        String userId,
        String userMessage,
        String assistantMessage,
        Boolean sttSuccess,
        Boolean ttsSuccess
) {
}
