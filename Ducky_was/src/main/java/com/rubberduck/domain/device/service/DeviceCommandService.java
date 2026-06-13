package com.rubberduck.domain.device.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.chat.repository.ConversationRepository;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceCommandStatus;
import com.rubberduck.domain.device.repository.DeviceCommandRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private static final List<DeviceCommandStatus> ACTIVE_STATUSES = List.of(
            DeviceCommandStatus.PENDING,
            DeviceCommandStatus.CLAIMED
    );

    private final DeviceCommandRepository deviceCommandRepository;
    private final DeviceService deviceService;
    private final ConversationRepository conversationRepository;

    @Transactional
    public DeviceCommand startRecording(User user, Long deviceId, Long conversationId) {
        Device device = deviceService.requireLinkedDevice(user, deviceId);
        ensureConversationOwner(user, conversationId);
        return deviceCommandRepository.findFirstByDeviceAndStatusInOrderByCreatedAtAsc(device, ACTIVE_STATUSES)
                .orElseGet(() -> deviceCommandRepository.save(DeviceCommand.startRecording(device, conversationId)));
    }

    @Transactional(readOnly = true)
    public Optional<DeviceCommand> latestForUser(User user, Long deviceId) {
        Device device = deviceService.requireLinkedDevice(user, deviceId);
        return deviceCommandRepository.findFirstByDeviceOrderByCreatedAtDesc(device);
    }

    @Transactional
    public Optional<DeviceCommand> claimNext(String deviceSerial) {
        Device device = deviceService.findOrCreateBySerial(deviceSerial);
        Optional<DeviceCommand> command =
                deviceCommandRepository.findFirstByDeviceAndStatusOrderByCreatedAtAsc(
                        device,
                        DeviceCommandStatus.PENDING
                );
        command.ifPresent(nextCommand -> {
            nextCommand.claim();
            deviceCommandRepository.save(nextCommand);
        });
        return command;
    }

    @Transactional
    public DeviceCommand complete(Long commandId, String deviceSerial, Boolean success, String errorMessage) {
        Device device = deviceService.findOrCreateBySerial(deviceSerial);
        DeviceCommand command = deviceCommandRepository.findById(commandId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        if (!command.getDevice().getId().equals(device.getId())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        command.complete(Boolean.TRUE.equals(success), errorMessage);
        return deviceCommandRepository.save(command);
    }

    private void ensureConversationOwner(User user, Long conversationId) {
        if (conversationId == null) {
            return;
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }
}
