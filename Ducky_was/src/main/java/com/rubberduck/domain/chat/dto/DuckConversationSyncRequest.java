package com.rubberduck.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DuckConversationSyncRequest(
        String deviceId,
        String userId,
        @JsonAlias("conversation_id") Long conversationId,
        String clientTurnId,
        String userMessage,
        String assistantMessage,
        String inputType,
        Boolean sttSuccess,
        Boolean ttsSuccess
) {
}
