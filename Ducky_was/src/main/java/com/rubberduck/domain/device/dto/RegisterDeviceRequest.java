package com.rubberduck.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RegisterDeviceRequest(
        @JsonAlias("serial_number") String serialNumber,
        @JsonAlias("firmware_version") String firmwareVersion
) {
}
