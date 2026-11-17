package com.rubberduck.domain.chat.dto;

public record EndConversationResponse(
        String conversationId,
        String status,
        MessageResponse feedbackMessage
) {
}
