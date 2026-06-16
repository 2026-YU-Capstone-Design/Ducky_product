package com.rubberduck.domain.iot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.chat.dto.ChatTurnResponse;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.service.DeviceService;
import com.rubberduck.domain.iot.entity.IotEvent;
import com.rubberduck.domain.iot.repository.IotEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IotService {

    private final IotEventRepository iotEventRepository;
    private final DeviceService deviceService;
    private final ChatService chatService;

    @Transactional
    public void saveState(String deviceId, String currentState) {
        Device device = deviceService.findOrCreateBySerial(deviceId);
        String normalizedState = normalizeState(currentState);
        deviceService.updateStatus(device, normalizedState);

        IotEvent event = IotEvent.create("STATE", device.getSerialNumber());
        event.setCurrentState(normalizedState);
        iotEventRepository.save(event);
    }

    @Transactional
    public ChatTurnResponse handleSttResult(String deviceId, String userId, Long conversationId, String sttText) {
        IotEvent event = IotEvent.create("STT_RESULT", deviceId);
        event.setUserId(userId);
        event.setConversationId(conversationId);
        event.setSttText(sttText);
        event.setSttSuccess(true);
        iotEventRepository.save(event);

        return chatService.sendDeviceMessage(userId, deviceId, conversationId, sttText, "voice", null);
    }

    @Transactional
    public void saveTtsComplete(String deviceId, Long messageId) {
        IotEvent event = IotEvent.create("TTS_COMPLETE", deviceId);
        event.setMessageId(messageId);
        event.setTtsSuccess(true);
        iotEventRepository.save(event);
    }

    @Transactional
    public void saveError(String deviceId, String errorCode, String errorMessage) {
        Device device = deviceService.findOrCreateBySerial(deviceId);
        deviceService.updateStatus(device, "ERROR");

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
            String deviceId,
            String userId,
            String userMessage,
            String assistantMessage,
            Boolean sttSuccess,
            Boolean ttsSuccess
    ) {
        chatService.recordConversationLog(conversationId, userMessage, assistantMessage, sttSuccess, ttsSuccess);

        IotEvent event = IotEvent.create("DUCK_LOG", deviceId);
        event.setConversationId(conversationId);
        event.setUserId(userId);
        event.setUserMessage(userMessage);
        event.setAssistantMessage(assistantMessage);
        event.setSttSuccess(sttSuccess);
        event.setTtsSuccess(ttsSuccess);
        iotEventRepository.save(event);
    }
}
