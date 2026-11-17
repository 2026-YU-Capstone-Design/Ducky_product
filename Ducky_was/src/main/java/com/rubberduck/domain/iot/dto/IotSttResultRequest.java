package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotSttResultRequest(
        @JsonAlias("device_id") String deviceId,
        @JsonAlias("user_id") String userId,
        @JsonAlias("conversation_id") Long conversationId,
        @JsonAlias("stt_text") String sttText
) {
}
