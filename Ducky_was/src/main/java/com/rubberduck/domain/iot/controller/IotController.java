package com.rubberduck.domain.iot.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.device.service.DeviceCommandService;
import com.rubberduck.domain.iot.dto.IotCommandCompleteRequest;
import com.rubberduck.domain.iot.dto.IotErrorRequest;
import com.rubberduck.domain.iot.dto.IotNextCommandRequest;
import com.rubberduck.domain.iot.dto.IotNextCommandResponse;
import com.rubberduck.domain.iot.dto.IotResultResponse;
import com.rubberduck.domain.iot.dto.IotStateRequest;
import com.rubberduck.domain.iot.dto.IotSttResultRequest;
import com.rubberduck.domain.iot.dto.IotSttResultResponse;
import com.rubberduck.domain.iot.dto.IotTtsCompleteRequest;
import com.rubberduck.domain.iot.service.IotService;
import com.rubberduck.global.response.ApiResponse;

import com.rubberduck.domain.device.entity.DeviceUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/iot")
@RequiredArgsConstructor
public class IotController {

    private final DeviceCommandService deviceCommandService;
    private final IotService iotService;

    @PostMapping("/state")
    public ApiResponse<IotResultResponse> state(@AuthenticationPrincipal DeviceUser link, @RequestBody IotStateRequest request) {
        link.requireSerial(request.deviceId());
        iotService.saveState(link, request.currentState());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/stt-result")
    public ApiResponse<IotSttResultResponse> sttResult(@AuthenticationPrincipal DeviceUser link, @RequestBody IotSttResultRequest request) {
        link.requireSerial(request.deviceId());
        return ApiResponse.ok(new IotSttResultResponse(
                iotService.handleSttResult(
                        link,
                        request.conversationId(),
                        request.sttText()
                )
        ));
    }

    @PostMapping("/tts-complete")
    public ApiResponse<IotResultResponse> ttsComplete(@AuthenticationPrincipal DeviceUser link, @RequestBody IotTtsCompleteRequest request) {
        link.requireSerial(request.deviceId());
        iotService.saveTtsComplete(link, request.messageId());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/error")
    public ApiResponse<IotResultResponse> error(@AuthenticationPrincipal DeviceUser link, @RequestBody IotErrorRequest request) {
        link.requireSerial(request.deviceId());
        iotService.saveError(link, request.errorCode(), request.errorMessage());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/commands/next")
    public ApiResponse<IotNextCommandResponse> nextCommand(@AuthenticationPrincipal DeviceUser link, @RequestBody IotNextCommandRequest request) {
        link.requireSerial(request.deviceId());
        return ApiResponse.ok(deviceCommandService.claimNext(link)
                .map(IotNextCommandResponse::from)
                .orElseGet(IotNextCommandResponse::none));
    }

    @PostMapping("/commands/{commandId}/complete")
    public ApiResponse<IotResultResponse> completeCommand(
            @AuthenticationPrincipal DeviceUser link,
            @PathVariable Long commandId,
            @RequestBody IotCommandCompleteRequest request
    ) {
        link.requireSerial(request.deviceId());
        deviceCommandService.complete(commandId, link, request.success(), request.errorMessage());
        return ApiResponse.ok(new IotResultResponse(true));
    }
}
