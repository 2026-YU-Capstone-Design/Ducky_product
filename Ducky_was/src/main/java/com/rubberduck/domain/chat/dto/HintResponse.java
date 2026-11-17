package com.rubberduck.domain.chat.dto;

public record HintResponse(
        String conversationId,
        MessageResponse hintMessage
) {
}
