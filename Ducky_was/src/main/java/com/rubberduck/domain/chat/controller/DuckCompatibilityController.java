package com.rubberduck.domain.chat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.dto.DuckActiveConversationResponse;
import com.rubberduck.domain.chat.dto.DuckConversationLogRequest;
import com.rubberduck.domain.chat.dto.DuckConversationLogResponse;
import com.rubberduck.domain.chat.dto.DuckConversationRequest;
import com.rubberduck.domain.chat.dto.DuckConversationResponse;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.iot.service.IotService;

import com.rubberduck.domain.device.entity.DeviceUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/duck")
@RequiredArgsConstructor
public class DuckCompatibilityController {

    private final ChatService chatService;
    private final IotService iotService;

    @GetMapping("/active-conversation")
    public DuckActiveConversationResponse activeConversation(
            @AuthenticationPrincipal DeviceUser link,
            @RequestParam String deviceId
    ) {
        link.requireSerial(deviceId);
        return chatService.findActiveConversationId(link)
                .map(DuckActiveConversationResponse::new)
                .orElseGet(() -> new DuckActiveConversationResponse(null));
    }

    @PostMapping("/conversation")
    public DuckConversationResponse conversation(@AuthenticationPrincipal DeviceUser link, @RequestBody DuckConversationRequest request) {
        link.requireSerial(request.deviceId());
        ChatTurnResponse turn = chatService.sendDeviceMessage(
                link,
                request.conversationId(),
                request.message(),
                request.inputType(),
                request.learningType()
        );
        return new DuckConversationResponse(
                Long.parseLong(turn.conversationId()),
                turn.aiResponse().type(),
                turn.aiResponse().content(),
                turn.aiResponse().hintLevel(),
                true
        );
    }

    @PostMapping("/conversation/logs")
    public DuckConversationLogResponse logs(@AuthenticationPrincipal DeviceUser link, @RequestBody DuckConversationLogRequest request) {
        link.requireSerial(request.deviceId());
        iotService.saveDuckLog(
                request.conversationId(),
                link,
                request.userMessage(),
                request.assistantMessage(),
                request.sttSuccess(),
                request.ttsSuccess()
        );
        return new DuckConversationLogResponse(true);
    }
}
