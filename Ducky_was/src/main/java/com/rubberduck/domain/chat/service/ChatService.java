package com.rubberduck.domain.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.dto.ConversationDetailResponse;
import com.rubberduck.domain.chat.dto.ConversationSummaryResponse;
import com.rubberduck.domain.chat.dto.EndConversationResponse;
import com.rubberduck.domain.chat.dto.HintResponse;
import com.rubberduck.domain.chat.dto.MessageResponse;
import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.chat.repository.ChatMessageRepository;
import com.rubberduck.domain.chat.repository.ConversationRepository;
import com.rubberduck.domain.chat.service.ChatResponseService.AiReply;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.domain.document.service.DocumentService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.service.UserService;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DeviceService deviceService;
    private final UserService userService;
    private final ChatResponseService chatResponseService;
    private final DocumentService documentService;

    @Transactional
    public ConversationSummaryResponse startConversation(
            User user,
            Long deviceId,
            Long personaId,
            String title,
            String topic
    ) {
        Device device = deviceId == null ? null : deviceService.getById(deviceId);
        Conversation conversation = Conversation.start(user, device, personaId, title, topic);
        return ConversationSummaryResponse.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(User user) {
        return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(User user, Long conversationId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);
        return toDetail(conversation);
    }

    @Transactional
    public ChatTurnResponse sendMessage(User user, Long conversationId, String messageText, String inputType) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);
        return appendUserTurn(user, conversation, messageText, inputType);
    }

    @Transactional
    public ChatTurnResponse sendDeviceMessage(
            String externalUserId,
            String deviceSerial,
            Long conversationId,
            String messageText,
            String inputType
    ) {
        Device device = deviceService.findOrCreateBySerial(deviceSerial);
        Conversation conversation;
        User user;

        if (conversationId != null) {
            conversation = getConversationEntity(conversationId);
            user = conversation.getUser();
        } else {
            user = deviceService.findLinkedUser(device)
                    .orElseGet(() -> userService.findOrCreateExternalUser(externalUserId));
            conversation = conversationRepository.findFirstByUserAndStatusOrderByUpdatedAtDesc(user, "in_progress")
                    .orElseGet(() -> conversationRepository.save(
                            Conversation.start(user, device, null, "Raspberry voice session", "음성 러버덕 질문 연결")
                    ));
        }

        if (conversation.getDevice() == null) {
            conversation.setDevice(device);
        }

        return appendUserTurn(user, conversation, messageText, inputType == null ? "voice" : inputType);
    }

    @Transactional
    public void recordConversationLog(
            Long conversationId,
            String userMessageText,
            String assistantMessageText,
            Boolean sttSuccess,
            Boolean ttsSuccess
    ) {
        if (conversationId == null) {
            return;
        }
        Conversation conversation = getConversationEntity(conversationId);
        List<ChatMessage> latestUserMessages =
                chatMessageRepository.findByConversationAndSenderOrderBySequenceNoDesc(conversation, "user");
        if (!latestUserMessages.isEmpty()) {
            ChatMessage latestUser = latestUserMessages.get(0);
            latestUser.setSttText(userMessageText);
            latestUser.setSttSuccess(sttSuccess);
        }

        List<ChatMessage> latestAssistantMessages =
                chatMessageRepository.findByConversationAndSenderOrderBySequenceNoDesc(conversation, "assistant");
        if (!latestAssistantMessages.isEmpty()) {
            ChatMessage latestAssistant = latestAssistantMessages.get(0);
            latestAssistant.setTtsText(assistantMessageText);
            latestAssistant.setTtsSuccess(ttsSuccess);
        }
    }

    private ChatTurnResponse appendUserTurn(User user, Conversation conversation, String messageText, String inputType) {
        String normalizedText = requireText(messageText);
        boolean completion = isCompletionText(normalizedText);
        int userSequence = conversation.getMessageCount() + 1;

        ChatMessage userMessage = ChatMessage.create(
                conversation,
                "user",
                normalizedText,
                completion ? "answer" : "question",
                normalizeInputType(inputType),
                userSequence
        );
        if ("voice".equals(normalizeInputType(inputType))) {
            userMessage.setSttText(normalizedText);
        }
        conversation.increaseMessageCount();

        String documentContext = completion
                ? ""
                : documentService.toPromptContext(documentService.searchResults(user, normalizedText, 3));
        AiReply aiReply = completion
                ? new AiReply(chatResponseService.generateCompletionFeedback(user, conversation), "feedback")
                : chatResponseService.generateTurn(normalizedText, user, documentContext);

        ChatMessage aiMessage = ChatMessage.create(
                conversation,
                "assistant",
                aiReply.content(),
                aiReply.type(),
                "system",
                userSequence + 1
        );
        conversation.increaseMessageCount();

        if (completion) {
            conversation.complete();
        }

        chatMessageRepository.save(userMessage);
        chatMessageRepository.save(aiMessage);
        conversationRepository.save(conversation);

        return new ChatTurnResponse(
                String.valueOf(conversation.getId()),
                MessageResponse.from(userMessage),
                MessageResponse.from(aiMessage)
        );
    }

    @Transactional
    public HintResponse requestHint(User user, Long conversationId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);

        int hintNumber = conversation.getHintCount() + 1;
        int hintLevel = Math.min(((hintNumber - 1) / 2) + 1, 3);
        ChatMessage hintMessage = ChatMessage.create(
                conversation,
                "assistant",
                chatResponseService.generateHint(conversation, hintNumber),
                "hint",
                "system",
                conversation.getMessageCount() + 1
        );
        hintMessage.setHintNumber(hintNumber);
        hintMessage.setHintLevel(hintLevel);

        conversation.increaseHintCount();
        conversation.increaseMessageCount();

        chatMessageRepository.save(hintMessage);
        conversationRepository.save(conversation);

        return new HintResponse(String.valueOf(conversation.getId()), MessageResponse.from(hintMessage));
    }

    @Transactional
    public EndConversationResponse endConversation(User user, Long conversationId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);

        if (!"completed".equals(conversation.getStatus())) {
            conversation.complete();
        }

        ChatMessage feedbackMessage = ChatMessage.create(
                conversation,
                "assistant",
                chatResponseService.generateCompletionFeedback(user, conversation),
                "feedback",
                "system",
                conversation.getMessageCount() + 1
        );
        conversation.increaseMessageCount();

        chatMessageRepository.save(feedbackMessage);
        conversationRepository.save(conversation);

        return new EndConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getStatus(),
                MessageResponse.from(feedbackMessage)
        );
    }

    private Conversation getConversationEntity(Long conversationId) {
        if (conversationId == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
    }

    private void ensureOwner(Conversation conversation, User user) {
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private ConversationDetailResponse toDetail(Conversation conversation) {
        List<MessageResponse> messages = chatMessageRepository.findByConversationOrderBySequenceNoAsc(conversation)
                .stream()
                .map(MessageResponse::from)
                .toList();
        ConversationSummaryResponse summary = ConversationSummaryResponse.from(conversation);
        return new ConversationDetailResponse(
                summary.id(),
                summary.title(),
                summary.topic(),
                summary.status(),
                summary.summary(),
                summary.hintCount(),
                summary.messageCount(),
                summary.startedAt(),
                summary.updatedAt(),
                summary.completedAt(),
                messages
        );
    }

    private String requireText(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return messageText.trim();
    }

    private String normalizeInputType(String inputType) {
        if (inputType == null || inputType.isBlank()) {
            return "text";
        }
        return inputType.trim();
    }

    private boolean isCompletionText(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return normalized.contains("알겠어")
                || normalized.contains("이해했어")
                || normalized.contains("끝낼게")
                || normalized.contains("끝내")
                || normalized.contains("그만")
                || normalized.contains("완료");
    }
}
