package com.rubberduck.domain.device.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.device.dto.DeviceLinkResponse;
import com.rubberduck.domain.device.dto.DeviceResponse;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceRepository;
import com.rubberduck.domain.device.repository.DeviceUserRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.service.UserService;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final UserService userService;

    @Transactional
    public Device register(String serialNumber, String firmwareVersion) {
        String normalizedSerial = required(serialNumber);
        return deviceRepository.findBySerialNumber(normalizedSerial)
                .orElseGet(() -> deviceRepository.save(Device.create(normalizedSerial, firmwareVersion)));
    }

    @Transactional
    public Device findOrCreateBySerial(String serialNumber) {
        String normalizedSerial = serialNumber == null || serialNumber.isBlank()
                ? "raspberry-duck-001"
                : serialNumber.trim();
        return deviceRepository.findBySerialNumber(normalizedSerial)
                .orElseGet(() -> deviceRepository.save(Device.create(normalizedSerial, null)));
    }

    @Transactional(readOnly = true)
    public Device getById(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
    }

    @Transactional
    public DeviceLinkResponse link(Long deviceId, String userId, String role) {
        Device device = getById(deviceId);
        User user = userService.resolveUserReference(userId);
        DeviceUser deviceUser = deviceUserRepository.findByDeviceAndUser(device, user)
                .orElseGet(() -> deviceUserRepository.save(DeviceUser.link(device, user, role)));
        return DeviceLinkResponse.from(deviceUser);
    }

    @Transactional
    public Device updateStatus(Device device, String status) {
        device.setStatus(status == null || status.isBlank() ? "UNKNOWN" : status.trim());
        return deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listForUser(User user) {
        return deviceUserRepository.findByUser(user).stream()
                .map(DeviceUser::getDevice)
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<User> findLinkedUser(Device device) {
        return deviceUserRepository.findFirstByDeviceOrderByIdDesc(device)
                .map(DeviceUser::getUser);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value.trim();
    }
}
