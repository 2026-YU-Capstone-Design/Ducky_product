package com.rubberduck.domain.chat.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.dto.DuckConversationLogRequest;
import com.rubberduck.domain.chat.dto.DuckConversationLogResponse;
import com.rubberduck.domain.chat.dto.DuckConversationRequest;
import com.rubberduck.domain.chat.dto.DuckConversationResponse;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.iot.service.IotService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/duck")
@RequiredArgsConstructor
public class DuckCompatibilityController {

    private final ChatService chatService;
    private final IotService iotService;

    @PostMapping("/conversation")
    public DuckConversationResponse conversation(@RequestBody DuckConversationRequest request) {
        ChatTurnResponse turn = chatService.sendDeviceMessage(
                request.userId(),
                request.deviceId(),
                null,
                request.message(),
                request.inputType()
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
    public DuckConversationLogResponse logs(@RequestBody DuckConversationLogRequest request) {
        iotService.saveDuckLog(
                request.conversationId(),
                request.deviceId(),
                request.userId(),
                request.userMessage(),
                request.assistantMessage(),
                request.sttSuccess(),
                request.ttsSuccess()
        );
        return new DuckConversationLogResponse(true);
    }
}
