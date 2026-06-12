package com.rubberduck.domain.device.dto;

import com.rubberduck.domain.device.entity.DeviceUser;

public record DeviceLinkResponse(
        String deviceUserId
) {

    public static DeviceLinkResponse from(DeviceUser deviceUser) {
        return new DeviceLinkResponse(String.valueOf(deviceUser.getId()));
    }
}
