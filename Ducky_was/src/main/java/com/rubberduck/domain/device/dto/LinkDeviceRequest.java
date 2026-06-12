package com.rubberduck.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LinkDeviceRequest(
        @JsonAlias("user_id") String userId,
        String role
) {
}
