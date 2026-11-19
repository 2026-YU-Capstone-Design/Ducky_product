package com.rubberduck.domain.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.domain.document.service.DocumentService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DeviceService deviceService;
    private final ChatResponseService chatResponseService;
    private final DocumentService documentService;
    private final ConversationRouter conversationRouter;

    @Transactional
    public ConversationSummaryResponse startConversation(
            User user,
            Long deviceId,
            Long personaId,
            String title,
            String topic
    ) {
        Device device = deviceId == null ? null : deviceService.requireLinkedDevice(user, deviceId);
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
        return sendMessage(user, conversationId, messageText, inputType, null);
    }

    @Transactional
    public ChatTurnResponse sendMessage(User user, Long conversationId, String messageText, String inputType, List<Long> documentIds) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);
        if (documentIds != null) {
            documentIds.forEach(id -> documentService.get(user, id));
            conversation.setSelectedDocumentIdsJson(new tools.jackson.databind.ObjectMapper().writeValueAsString(documentIds));
        }
        return appendUserTurn(user, conversation, messageText, inputType, null, documentIds != null && !documentIds.isEmpty());
    }

    @Transactional
    public ChatTurnResponse sendDeviceMessage(
            DeviceUser link,
            Long conversationId,
            String messageText,
            String inputType,
            Map<String, String> learningType
    ) {
        Device device = link.getDevice();
        User user = link.getUser();
        Long targetId = conversationId != null ? conversationId : findActiveConversationId(link).orElse(null);
        Conversation conversation;
        if (targetId == null) {
            conversation = conversationRepository.save(
                    Conversation.start(user, device, null, "Raspberry voice session", "음성 러버덕 질문 연결"));
        } else {
            conversation = lockAssignableConversation(user, device, targetId);
            conversation.setDevice(device);
        }

        return appendUserTurn(user, conversation, messageText, inputType == null ? "voice" : inputType, learningType, false);
    }

    @Transactional
    public Conversation lockAssignableConversation(User user, Device device, Long conversationId) {
        Conversation conversation = conversationRepository.findLockedById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        ensureOwner(conversation, user);
        if (conversation.getDevice() != null && !conversation.getDevice().getId().equals(device.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return conversation;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findActiveConversationId(DeviceUser link) {
        User user = link.getUser();
        return conversationRepository.findFirstByUserAndDeviceAndStatusOrderByUpdatedAtDesc(user, link.getDevice(), "in_progress")
                .or(() -> conversationRepository.findFirstByUserAndDeviceAndStatusOrderByUpdatedAtDesc(user, null, "in_progress"))
                .map(ConversationRepository.ConversationId::getId);
    }

    public void requireDeviceConversation(DeviceUser link, Conversation conversation) {
        ensureOwner(conversation, link.getUser());
        if (conversation.getDevice() == null || !conversation.getDevice().getId().equals(link.getDevice().getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional
    public void recordConversationLog(
            DeviceUser link,
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
        requireDeviceConversation(link, conversation);
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

    private ChatTurnResponse appendUserTurn(
            User user,
            Conversation conversation,
            String messageText,
            String inputType,
            Map<String, String> learningType,
            boolean explicitDocumentContext
    ) {
        String normalizedText = requireText(messageText);
        boolean completion = isCompletionText(normalizedText);
        List<ChatMessage> conversationHistory = chatMessageRepository.findByConversationOrderBySequenceNoAsc(conversation);
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

        List<Long> selectedIds = conversation.getSelectedDocumentIdsJson() == null ? null
                : new tools.jackson.databind.ObjectMapper().readValue(conversation.getSelectedDocumentIdsJson(),
                    new tools.jackson.core.type.TypeReference<List<Long>>() {});
        var decision = conversationRouter.decide(normalizedText, conversationHistory, explicitDocumentContext);
        var sources = !completion && decision.needsDocuments()
                ? documentService.search(user, decision.searchQuery(), 3, selectedIds).results()
                : java.util.List.<com.rubberduck.domain.document.dto.DocumentSearchResult>of();
        String documentContext = documentService.toPromptContext(sources);
        AiReply aiReply = completion
                ? new AiReply(chatResponseService.generateCompletionFeedback(user, conversation, conversationHistory), "feedback", chatResponseService.responseMode())
                : chatResponseService.generateRoutedTurn(normalizedText, user, documentContext, conversationHistory, learningType, decision);

        ChatMessage aiMessage = ChatMessage.create(
                conversation,
                "assistant",
                aiReply.content(),
                aiReply.type(),
                "system",
                userSequence + 1
        );
        aiMessage.setSources(sources);
        aiMessage.setResponseStrategy(completion ? "COMPLETION" : decision.needsDocuments() && sources.isEmpty()
                ? "INSUFFICIENT_EVIDENCE" : decision.mode().name());
        if ("hint".equals(aiReply.type())) {
            int number = conversation.getHintCount() + 1;
            aiMessage.setHintNumber(number);
            aiMessage.setHintLevel(Math.min(((number - 1) / 2) + 1, 3));
            conversation.increaseHintCount();
        }
        aiMessage.setResponseMode(aiReply.responseMode());
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

        List<ChatMessage> conversationHistory = chatMessageRepository.findByConversationOrderBySequenceNoAsc(conversation);
        List<ChatMessage> previousHints = conversationHistory.stream()
                .filter(message -> "hint".equals(message.getMessageType()))
                .toList();
        int hintNumber = conversation.getHintCount() + 1;
        int hintLevel = Math.min(((hintNumber - 1) / 2) + 1, 3);
        List<Long> selectedIds = conversation.getSelectedDocumentIdsJson() == null ? null
                : new tools.jackson.databind.ObjectMapper().readValue(conversation.getSelectedDocumentIdsJson(),
                    new tools.jackson.core.type.TypeReference<List<Long>>() {});
        var routed = conversationRouter.decide("다음 힌트", conversationHistory);
        var sources = routed.needsDocuments()
                ? documentService.search(user, routed.searchQuery(), 3, selectedIds).results()
                : java.util.List.<com.rubberduck.domain.document.dto.DocumentSearchResult>of();
        AiReply reply = routed.needsDocuments() || routed.mode() == ConversationRouter.Mode.CLARIFY
                ? chatResponseService.generateRoutedTurn("다음 힌트", user, documentService.toPromptContext(sources), conversationHistory, null, routed)
                : new AiReply(chatResponseService.generateHint(user, conversation, hintNumber, conversationHistory, previousHints), "hint", chatResponseService.responseMode());
        ChatMessage hintMessage = ChatMessage.create(conversation, "assistant", reply.content(), reply.type(), "system", conversation.getMessageCount() + 1);
        hintMessage.setSources(sources);
        hintMessage.setResponseStrategy(routed.needsDocuments() && sources.isEmpty() ? "INSUFFICIENT_EVIDENCE" : routed.mode().name());
        hintMessage.setResponseMode(reply.responseMode());
        if ("hint".equals(reply.type())) {
            hintMessage.setHintNumber(hintNumber);
            hintMessage.setHintLevel(hintLevel);
            conversation.increaseHintCount();
        }
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

        List<ChatMessage> conversationHistory = chatMessageRepository.findByConversationOrderBySequenceNoAsc(conversation);
        ChatMessage feedbackMessage = ChatMessage.create(
                conversation,
                "assistant",
                chatResponseService.generateCompletionFeedback(user, conversation, conversationHistory),
                "feedback",
                "system",
                conversation.getMessageCount() + 1
        );
        feedbackMessage.setResponseMode(chatResponseService.responseMode());
        conversation.increaseMessageCount();

        chatMessageRepository.save(feedbackMessage);
        conversationRepository.save(conversation);

        return new EndConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getStatus(),
                MessageResponse.from(feedbackMessage)
        );
    }

    @Transactional
    public void deleteConversation(User user, Long conversationId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureOwner(conversation, user);
        chatMessageRepository.deleteByConversation(conversation);
        conversationRepository.delete(conversation);
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
            throw new CustomException(ErrorCode.FORBIDDEN);
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
        return normalized.equals("알겠어")
                || normalized.equals("이해했어")
                || normalized.equals("끝낼게")
                || normalized.equals("끝내")
                || normalized.equals("그만")
                || normalized.equals("완료");
    }
}
