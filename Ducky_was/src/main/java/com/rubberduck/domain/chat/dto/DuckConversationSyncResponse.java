package com.rubberduck.domain.chat.dto;

public record DuckConversationSyncResponse(
        Long conversationId,
        boolean synced
) {
}
