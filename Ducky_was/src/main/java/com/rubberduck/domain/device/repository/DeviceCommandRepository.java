package com.rubberduck.domain.device.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceCommandStatus;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    Optional<DeviceCommand> findFirstByDeviceAndRequestedByAndStatusInOrderByCreatedAtAsc(
            Device device,
            User requestedBy,
            Collection<DeviceCommandStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceCommand> findFirstByDeviceAndRequestedByAndStatusOrderByCreatedAtAsc(
            Device device,
            User requestedBy,
            DeviceCommandStatus status
    );

    Optional<DeviceCommand> findFirstByDeviceAndRequestedByOrderByCreatedAtDesc(Device device, User requestedBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceCommand> findLockedById(Long id);
}
