package com.rubberduck.domain.chat.dto;

import com.rubberduck.domain.chat.entity.ChatMessage;

public record MessageResponse(
        String id,
        String role,
        String content,
        String type,
        String createdAt,
        Integer hintLevel,
        Integer hintNumber
) {

    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                String.valueOf(message.getId()),
                message.getSender(),
                message.getMessageText(),
                message.getMessageType(),
                message.getCreatedAt().toString(),
                message.getHintLevel(),
                message.getHintNumber()
        );
    }
}
