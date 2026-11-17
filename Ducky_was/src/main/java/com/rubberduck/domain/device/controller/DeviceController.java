package com.rubberduck.domain.device.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.auth.service.AuthService;
import com.rubberduck.domain.device.dto.DeviceCommandResponse;
import com.rubberduck.domain.device.dto.DeviceLinkResponse;
import com.rubberduck.domain.device.dto.DeviceResponse;
import com.rubberduck.domain.device.dto.LinkDeviceRequest;
import com.rubberduck.domain.device.dto.RegisterDeviceRequest;
import com.rubberduck.domain.device.dto.StartDeviceCommandRequest;
import com.rubberduck.domain.device.service.DeviceCommandService;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final AuthService authService;
    private final DeviceCommandService deviceCommandService;
    private final DeviceService deviceService;

    @PostMapping
    public ApiResponse<DeviceResponse> register(@RequestBody RegisterDeviceRequest request) {
        return ApiResponse.ok(DeviceResponse.from(deviceService.register(request.serialNumber(), request.firmwareVersion())));
    }

    @PostMapping("/{deviceId}/link")
    public ApiResponse<DeviceLinkResponse> link(
            @PathVariable Long deviceId,
            @RequestBody LinkDeviceRequest request
    ) {
        return ApiResponse.ok(deviceService.link(deviceId, request.userId(), request.role()));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.ok(deviceService.listForUser(authService.requireUser(authorization)));
    }

    @PostMapping("/{deviceId}/commands/start-recording")
    public ApiResponse<DeviceCommandResponse> startRecordingCommand(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long deviceId,
            @RequestBody StartDeviceCommandRequest request
    ) {
        return ApiResponse.ok(DeviceCommandResponse.from(deviceCommandService.startRecording(
                authService.requireUser(authorization),
                deviceId,
                request.conversationId()
        )));
    }

    @GetMapping("/{deviceId}/commands/latest")
    public ApiResponse<DeviceCommandResponse> latestCommand(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long deviceId
    ) {
        return ApiResponse.ok(deviceCommandService.latestForUser(authService.requireUser(authorization), deviceId)
                .map(DeviceCommandResponse::from)
                .orElse(null));
    }
}
