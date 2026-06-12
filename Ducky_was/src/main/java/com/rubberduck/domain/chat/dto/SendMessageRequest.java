package com.rubberduck.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SendMessageRequest(
        @JsonAlias("conversation_id") Long conversationId,
        @JsonAlias("message_text") String messageText,
        String message,
        @JsonAlias("input_type") String inputType
) {

    public String effectiveMessageText() {
        if (messageText != null && !messageText.isBlank()) {
            return messageText;
        }
        return message;
    }
}
