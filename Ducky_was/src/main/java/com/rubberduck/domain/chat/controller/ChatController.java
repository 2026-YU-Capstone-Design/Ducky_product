package com.rubberduck.domain.chat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.auth.service.AuthService;
import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.dto.ConversationDetailResponse;
import com.rubberduck.domain.chat.dto.ConversationSummaryResponse;
import com.rubberduck.domain.chat.dto.EndConversationResponse;
import com.rubberduck.domain.chat.dto.HintRequest;
import com.rubberduck.domain.chat.dto.HintResponse;
import com.rubberduck.domain.chat.dto.SendMessageRequest;
import com.rubberduck.domain.chat.dto.StartConversationRequest;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AuthService authService;
    private final ChatService chatService;

    @PostMapping("/conversations")
    public ApiResponse<ConversationSummaryResponse> startConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody StartConversationRequest request
    ) {
        User user = authService.requireUser(authorization);
        return ApiResponse.ok(chatService.startConversation(
                user,
                request.deviceId(),
                request.personaId(),
                request.title(),
                request.topic()
        ));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSummaryResponse>> listConversations(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.ok(chatService.listConversations(authService.requireUser(authorization)));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long conversationId
    ) {
        return ApiResponse.ok(chatService.getConversation(authService.requireUser(authorization), conversationId));
    }

    @PostMapping("/messages")
    public ApiResponse<ChatTurnResponse> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SendMessageRequest request
    ) {
        User user = authService.requireUser(authorization);
        return ApiResponse.ok(chatService.sendMessage(
                user,
                request.conversationId(),
                request.effectiveMessageText(),
                request.inputType()
        ));
    }

    @PostMapping("/hints")
    public ApiResponse<HintResponse> requestHint(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody HintRequest request
    ) {
        return ApiResponse.ok(chatService.requestHint(authService.requireUser(authorization), request.conversationId()));
    }

    @PatchMapping("/conversations/{conversationId}/end")
    public ApiResponse<EndConversationResponse> endConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long conversationId
    ) {
        return ApiResponse.ok(chatService.endConversation(authService.requireUser(authorization), conversationId));
    }
}
