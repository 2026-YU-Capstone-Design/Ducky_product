package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotTtsCompleteRequest(
        @JsonAlias("device_id") String deviceId,
        @JsonAlias("message_id") Long messageId
) {
}
