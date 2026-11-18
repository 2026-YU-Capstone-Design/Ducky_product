package com.rubberduck.domain.device.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.device.dto.DeviceLinkResponse;
import com.rubberduck.domain.device.dto.DeviceResponse;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceRepository;
import com.rubberduck.domain.device.repository.DeviceUserRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceUserRepository deviceUserRepository;

    public Device registeredDevice(User user, String serialNumber) {
        Device device = deviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
        return requireLink(user, device).getDevice();
    }

    public Device requireLinkedDevice(User user, Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        return requireLink(user, device).getDevice();
    }

    public DeviceLinkResponse link(User user, Long deviceId, String userId, String role) {
        if (!String.valueOf(user.getId()).equals(userId) || (role != null && !"OWNER".equals(role))) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return DeviceLinkResponse.from(requireLink(user, deviceRepository.getReferenceById(deviceId)));
    }

    private DeviceUser requireLink(User user, Device device) {
        return deviceUserRepository.findByDeviceAndUserAndTokenHashIsNotNull(device, user)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
    }

    public List<DeviceResponse> listForUser(User user) {
        return deviceUserRepository.findByUserAndTokenHashIsNotNullOrderByIdAsc(user).stream()
                .map(DeviceUser::getDevice)
                .map(DeviceResponse::from)
                .toList();
    }
}
