package com.rubberduck.domain.device.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceCommand;
import com.rubberduck.domain.device.entity.DeviceCommandStatus;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    Optional<DeviceCommand> findFirstByDeviceAndStatusInOrderByCreatedAtAsc(
            Device device,
            Collection<DeviceCommandStatus> statuses
    );

    Optional<DeviceCommand> findFirstByDeviceAndStatusOrderByCreatedAtAsc(
            Device device,
            DeviceCommandStatus status
    );

    Optional<DeviceCommand> findFirstByDeviceOrderByCreatedAtDesc(Device device);
}
