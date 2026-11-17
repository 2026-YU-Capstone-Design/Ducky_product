package com.rubberduck.domain.chat.dto;

public record ChatTurnResponse(
        String conversationId,
        MessageResponse userMessage,
        MessageResponse aiResponse
) {
}
