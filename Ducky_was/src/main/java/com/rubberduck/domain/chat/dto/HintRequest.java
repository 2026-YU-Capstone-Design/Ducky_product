package com.rubberduck.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record HintRequest(
        @JsonAlias("conversation_id") Long conversationId
) {
}
