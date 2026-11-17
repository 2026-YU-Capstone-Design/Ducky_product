package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotErrorRequest(
        @JsonAlias("device_id") String deviceId,
        String errorCode,
        String errorMessage
) {
}
