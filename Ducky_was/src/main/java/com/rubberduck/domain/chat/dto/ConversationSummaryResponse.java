package com.rubberduck.domain.chat.dto;

import java.time.Instant;

import com.rubberduck.domain.chat.entity.Conversation;

public record ConversationSummaryResponse(
        String id,
        String title,
        String topic,
        String status,
        String summary,
        int hintCount,
        int messageCount,
        String startedAt,
        String updatedAt,
        String completedAt
) {

    public static ConversationSummaryResponse from(Conversation conversation) {
        return new ConversationSummaryResponse(
                String.valueOf(conversation.getId()),
                conversation.getTitle(),
                conversation.getTopic(),
                conversation.getStatus(),
                conversation.getSummary(),
                conversation.getHintCount(),
                conversation.getMessageCount(),
                format(conversation.getStartedAt()),
                format(conversation.getUpdatedAt()),
                format(conversation.getCompletedAt())
        );
    }

    private static String format(Instant value) {
        return value == null ? null : value.toString();
    }
}
