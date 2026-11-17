package com.rubberduck.domain.iot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceRepository;
import com.rubberduck.domain.chat.repository.ChatMessageRepository;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;
import com.rubberduck.domain.iot.entity.IotEvent;
import com.rubberduck.domain.iot.repository.IotEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IotService {

    private final IotEventRepository iotEventRepository;
    private final DeviceRepository devices;
    private final ChatMessageRepository messages;
    private final ChatService chatService;

    @Transactional
    public void saveState(DeviceUser link, String currentState) {
        Device device = devices.getReferenceById(link.getDevice().getId());
        String normalizedState = normalizeState(currentState);
        device.setStatus(normalizedState);

        IotEvent event = IotEvent.create("STATE", device.getSerialNumber());
        event.setCurrentState(normalizedState);
        iotEventRepository.save(event);
    }

    @Transactional
    public ChatTurnResponse handleSttResult(DeviceUser link, Long conversationId, String sttText) {
        IotEvent event = IotEvent.create("STT_RESULT", link.getDevice().getSerialNumber());
        event.setUserId(String.valueOf(link.getUser().getId()));
        event.setConversationId(conversationId);
        event.setSttText(sttText);
        event.setSttSuccess(true);
        iotEventRepository.save(event);

        return chatService.sendDeviceMessage(link, conversationId, sttText, "voice", null);
    }

    @Transactional
    public void saveTtsComplete(DeviceUser link, Long messageId) {
        if (messageId != null) {
            var message = messages.findById(messageId).orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
            chatService.requireDeviceConversation(link, message.getConversation());
        }
        IotEvent event = IotEvent.create("TTS_COMPLETE", link.getDevice().getSerialNumber());
        event.setMessageId(messageId);
        event.setTtsSuccess(true);
        iotEventRepository.save(event);
    }

    @Transactional
    public void saveError(DeviceUser link, String errorCode, String errorMessage) {
        Device device = devices.getReferenceById(link.getDevice().getId());
        device.setStatus("ERROR");

        IotEvent event = IotEvent.create("ERROR", device.getSerialNumber());
        event.setCurrentState("ERROR");
        event.setErrorCode(errorCode);
        event.setErrorMessage(errorMessage);
        iotEventRepository.save(event);
    }

    private String normalizeState(String currentState) {
        if (currentState == null || currentState.isBlank()) {
            return "UNKNOWN";
        }
        return currentState.trim().toUpperCase();
    }

    @Transactional
    public void saveDuckLog(
            Long conversationId,
            DeviceUser link,
            String userMessage,
            String assistantMessage,
            Boolean sttSuccess,
            Boolean ttsSuccess
    ) {
        chatService.recordConversationLog(link, conversationId, userMessage, assistantMessage, sttSuccess, ttsSuccess);

        IotEvent event = IotEvent.create("DUCK_LOG", link.getDevice().getSerialNumber());
        event.setConversationId(conversationId);
        event.setUserId(String.valueOf(link.getUser().getId()));
        event.setUserMessage(userMessage);
        event.setAssistantMessage(assistantMessage);
        event.setSttSuccess(sttSuccess);
        event.setTtsSuccess(ttsSuccess);
        iotEventRepository.save(event);
    }
}
