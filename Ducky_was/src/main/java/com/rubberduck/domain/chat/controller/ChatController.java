package com.rubberduck.domain.chat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final ChatService chatService;

    @PostMapping("/conversations")
    public ApiResponse<ConversationSummaryResponse> startConversation(
            @AuthenticationPrincipal User user,
            @RequestBody StartConversationRequest request
    ) {
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
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.ok(chatService.listConversations(user));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId
    ) {
        return ApiResponse.ok(chatService.getConversation(user, conversationId));
    }

    @PostMapping("/messages")
    public ApiResponse<ChatTurnResponse> sendMessage(
            @AuthenticationPrincipal User user,
            @RequestBody SendMessageRequest request
    ) {
        return ApiResponse.ok(chatService.sendMessage(
                user,
                request.conversationId(),
                request.effectiveMessageText(),
                request.inputType()
        ));
    }

    @PostMapping("/hints")
    public ApiResponse<HintResponse> requestHint(
            @AuthenticationPrincipal User user,
            @RequestBody HintRequest request
    ) {
        return ApiResponse.ok(chatService.requestHint(user, request.conversationId()));
    }

    @PatchMapping("/conversations/{conversationId}/end")
    public ApiResponse<EndConversationResponse> endConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId
    ) {
        return ApiResponse.ok(chatService.endConversation(user, conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId
    ) {
        chatService.deleteConversation(user, conversationId);
        return ApiResponse.ok();
    }
}
