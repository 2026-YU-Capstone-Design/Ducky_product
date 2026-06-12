package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotStateRequest(
        @JsonAlias("device_id") String deviceId,
        @JsonAlias("current_state") String currentState
) {
}
