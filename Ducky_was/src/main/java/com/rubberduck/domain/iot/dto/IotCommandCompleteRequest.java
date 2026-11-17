package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotCommandCompleteRequest(
        @JsonAlias("device_id") String deviceId,
        Boolean success,
        String errorMessage
) {
}
