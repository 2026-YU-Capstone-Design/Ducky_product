package com.rubberduck.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SendMessageRequest(
        @JsonAlias("conversation_id") Long conversationId,
        @JsonAlias("message_text") String messageText,
        String message,
        @JsonAlias("input_type") String inputType,
        @JsonAlias("document_ids") java.util.List<Long> documentIds
) {

    public String effectiveMessageText() {
        if (messageText != null && !messageText.isBlank()) {
            return messageText;
        }
        return message;
    }
}
