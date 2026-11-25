package com.rubberduck.domain.device.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
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
    private static final List<String> BUSY_DEVICE_STATUSES = List.of(
            "RECORDING", "TRANSCRIBING", "THINKING", "SPEAKING", "LOGGING");

    private final DeviceCommandRepository commands;
    private final DeviceService deviceService;
    private final ChatService chatService;
    private final DeviceRepository devices;

    @Value("${device.commands.pending-timeout:PT2M}")
    private Duration pendingTimeout;

    @Value("${device.commands.claimed-timeout:PT5M}")
    private Duration claimedTimeout;

    @Transactional
    public DeviceCommand startRecording(User user, Long deviceId, Long conversationId) {
        Device device = devices.findLockedById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        deviceService.requireLinkedDevice(user, deviceId);
        expireAbandoned(device, user);
        Conversation conversation = conversationId == null ? null
                : chatService.lockAssignableConversation(user, device, conversationId);
        return commands.findFirstByDeviceAndRequestedByAndStatusInOrderByCreatedAtAscIdAsc(device, user, ACTIVE_STATUSES)
                .orElseGet(() -> {
                    if (conversation != null) {
                        conversation.setDevice(device);
                    }
                    return commands.save(DeviceCommand.startRecording(device, user, conversationId));
                });
    }

    @Transactional
    public Optional<DeviceCommand> latestForUser(User user, Long deviceId) {
        Device device = devices.findLockedById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        deviceService.requireLinkedDevice(user, deviceId);
        expireAbandoned(device, user);
        return commands.findFirstByDeviceAndRequestedByOrderByCreatedAtDescIdDesc(device, user);
    }

    @Transactional
    public Optional<DeviceCommand> claimNext(DeviceUser link) {
        Device device = lockDevice(link);
        expireAbandoned(device, link.getUser());
        Optional<DeviceCommand> command = commands.findFirstByDeviceAndRequestedByAndStatusOrderByCreatedAtAscIdAsc(
                device, link.getUser(), DeviceCommandStatus.PENDING);
        command.ifPresent(DeviceCommand::claim);
        return command;
    }

    @Transactional
    public DeviceCommand complete(Long commandId, DeviceUser link, Boolean success, String errorMessage) {
        lockDevice(link);
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

    private Device lockDevice(DeviceUser link) {
        return devices.findLockedById(link.getDevice().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
    }

    // Serialize expiration, claiming, and starting on the same device row. Expired
    // commands fail instead of being requeued, so a lost acknowledgement cannot
    // cause the same recording to execute twice.
    private void expireAbandoned(Device device, User user) {
        Instant now = Instant.now();
        Long latestId = commands.findFirstByDeviceAndRequestedByOrderByCreatedAtDescIdDesc(device, user)
                .map(DeviceCommand::getId).orElse(null);
        for (DeviceCommand command : commands.findAllByDeviceAndRequestedByAndStatusInOrderByCreatedAtAscIdAsc(
                device, user, ACTIVE_STATUSES)) {
            boolean claimed = command.getStatus() == DeviceCommandStatus.CLAIMED;
            Instant started = claimed && command.getClaimedAt() != null
                    ? command.getClaimedAt() : command.getCreatedAt();
            Duration timeout = claimed ? claimedTimeout : pendingTimeout;
            if (started != null && !started.plus(timeout).isAfter(now)) {
                command.complete(false, "녹음 명령 응답 시간이 초과되었습니다. 장치 연결을 확인한 뒤 다시 시도해 주세요.");
                if (claimed && command.getId().equals(latestId)
                        && BUSY_DEVICE_STATUSES.contains(device.getStatus())) {
                    device.setStatus("ERROR");
                }
            }
        }
    }
}
