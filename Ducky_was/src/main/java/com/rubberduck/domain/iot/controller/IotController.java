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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/iot")
@RequiredArgsConstructor
public class IotController {

    private final DeviceCommandService deviceCommandService;
    private final IotService iotService;

    @PostMapping("/state")
    public ApiResponse<IotResultResponse> state(@RequestBody IotStateRequest request) {
        iotService.saveState(request.deviceId(), request.currentState());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/stt-result")
    public ApiResponse<IotSttResultResponse> sttResult(@RequestBody IotSttResultRequest request) {
        return ApiResponse.ok(new IotSttResultResponse(
                iotService.handleSttResult(
                        request.deviceId(),
                        request.userId(),
                        request.conversationId(),
                        request.sttText()
                )
        ));
    }

    @PostMapping("/tts-complete")
    public ApiResponse<IotResultResponse> ttsComplete(@RequestBody IotTtsCompleteRequest request) {
        iotService.saveTtsComplete(request.deviceId(), request.messageId());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/error")
    public ApiResponse<IotResultResponse> error(@RequestBody IotErrorRequest request) {
        iotService.saveError(request.deviceId(), request.errorCode(), request.errorMessage());
        return ApiResponse.ok(new IotResultResponse(true));
    }

    @PostMapping("/commands/next")
    public ApiResponse<IotNextCommandResponse> nextCommand(@RequestBody IotNextCommandRequest request) {
        return ApiResponse.ok(deviceCommandService.claimNext(request.deviceId())
                .map(IotNextCommandResponse::from)
                .orElseGet(IotNextCommandResponse::none));
    }

    @PostMapping("/commands/{commandId}/complete")
    public ApiResponse<IotResultResponse> completeCommand(
            @PathVariable Long commandId,
            @RequestBody IotCommandCompleteRequest request
    ) {
        deviceCommandService.complete(commandId, request.deviceId(), request.success(), request.errorMessage());
        return ApiResponse.ok(new IotResultResponse(true));
    }
}
