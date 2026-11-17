package com.rubberduck.domain.device.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.device.dto.DeviceCommandResponse;
import com.rubberduck.domain.device.dto.DeviceLinkResponse;
import com.rubberduck.domain.device.dto.DeviceResponse;
import com.rubberduck.domain.device.dto.LinkDeviceRequest;
import com.rubberduck.domain.device.dto.RegisterDeviceRequest;
import com.rubberduck.domain.device.dto.StartDeviceCommandRequest;
import com.rubberduck.domain.device.service.DeviceCommandService;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceCommandService deviceCommandService;
    private final DeviceService deviceService;

    @PostMapping
    public ApiResponse<DeviceResponse> register(@AuthenticationPrincipal User user, @RequestBody RegisterDeviceRequest request) {
        return ApiResponse.ok(DeviceResponse.from(deviceService.registeredDevice(user, request.serialNumber())));
    }

    @PostMapping("/{deviceId}/link")
    public ApiResponse<DeviceLinkResponse> link(
            @AuthenticationPrincipal User user,
            @PathVariable Long deviceId,
            @RequestBody LinkDeviceRequest request
    ) {
        return ApiResponse.ok(deviceService.link(user, deviceId, request.userId(), request.role()));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> list(
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.ok(deviceService.listForUser(user));
    }

    @PostMapping("/{deviceId}/commands/start-recording")
    public ApiResponse<DeviceCommandResponse> startRecordingCommand(
            @AuthenticationPrincipal User user,
            @PathVariable Long deviceId,
            @RequestBody StartDeviceCommandRequest request
    ) {
        return ApiResponse.ok(DeviceCommandResponse.from(deviceCommandService.startRecording(
                user,
                deviceId,
                request.conversationId()
        )));
    }

    @GetMapping("/{deviceId}/commands/latest")
    public ApiResponse<DeviceCommandResponse> latestCommand(
            @AuthenticationPrincipal User user,
            @PathVariable Long deviceId
    ) {
        return ApiResponse.ok(deviceCommandService.latestForUser(user, deviceId)
                .map(DeviceCommandResponse::from)
                .orElse(null));
    }
}
