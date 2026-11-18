package com.rubberduck.domain.device.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceCommandStatus;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.device.repository.DeviceCommandRepository;
import com.rubberduck.domain.device.repository.DeviceRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private static final List<DeviceCommandStatus> ACTIVE_STATUSES = List.of(
            DeviceCommandStatus.PENDING, DeviceCommandStatus.CLAIMED);

    private final DeviceCommandRepository commands;
    private final DeviceService deviceService;
    private final ChatService chatService;
    private final DeviceRepository devices;

    @Transactional
    public DeviceCommand startRecording(User user, Long deviceId, Long conversationId) {
        Device device = devices.findLockedById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        deviceService.requireLinkedDevice(user, deviceId);
        Conversation conversation = conversationId == null ? null
                : chatService.lockAssignableConversation(user, device, conversationId);
        return commands.findFirstByDeviceAndRequestedByAndStatusInOrderByCreatedAtAsc(device, user, ACTIVE_STATUSES)
                .orElseGet(() -> {
                    if (conversation != null) {
                        conversation.setDevice(device);
                    }
                    return commands.save(DeviceCommand.startRecording(device, user, conversationId));
                });
    }

    @Transactional(readOnly = true)
    public Optional<DeviceCommand> latestForUser(User user, Long deviceId) {
        Device device = deviceService.requireLinkedDevice(user, deviceId);
        return commands.findFirstByDeviceAndRequestedByOrderByCreatedAtDesc(device, user);
    }

    @Transactional
    public Optional<DeviceCommand> claimNext(DeviceUser link) {
        Optional<DeviceCommand> command = commands.findFirstByDeviceAndRequestedByAndStatusOrderByCreatedAtAsc(
                link.getDevice(), link.getUser(), DeviceCommandStatus.PENDING);
        command.ifPresent(DeviceCommand::claim);
        return command;
    }

    @Transactional
    public DeviceCommand complete(Long commandId, DeviceUser link, Boolean success, String errorMessage) {
        DeviceCommand command = commands.findLockedById(commandId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        if (!command.getDevice().getId().equals(link.getDevice().getId()) || command.getRequestedBy() == null
                || !command.getRequestedBy().getId().equals(link.getUser().getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        boolean succeeded = Boolean.TRUE.equals(success);
        if (command.getStatus() == (succeeded ? DeviceCommandStatus.COMPLETED : DeviceCommandStatus.FAILED)) {
            return command;
        }
        if (command.getStatus() != DeviceCommandStatus.CLAIMED) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        command.complete(succeeded, errorMessage);
        return command;
    }
}
