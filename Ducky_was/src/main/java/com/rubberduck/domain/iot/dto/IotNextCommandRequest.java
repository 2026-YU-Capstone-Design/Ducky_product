package com.rubberduck.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record IotNextCommandRequest(
        @JsonAlias("device_id") String deviceId
) {
}
