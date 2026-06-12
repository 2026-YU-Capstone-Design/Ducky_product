package com.rubberduck.domain.chat.dto;

import java.util.List;

public record ConversationDetailResponse(
        String id,
        String title,
        String topic,
        String status,
        String summary,
        int hintCount,
        int messageCount,
        String startedAt,
        String updatedAt,
        String completedAt,
        List<MessageResponse> messages
) {
}
