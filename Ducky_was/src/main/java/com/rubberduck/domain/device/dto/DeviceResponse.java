package com.rubberduck.domain.device.dto;

import com.rubberduck.domain.device.entity.Device;

public record DeviceResponse(
        String id,
        String serialNumber,
        String firmwareVersion,
        String status
) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                String.valueOf(device.getId()),
                device.getSerialNumber(),
                device.getFirmwareVersion(),
                device.getStatus()
        );
    }
}
