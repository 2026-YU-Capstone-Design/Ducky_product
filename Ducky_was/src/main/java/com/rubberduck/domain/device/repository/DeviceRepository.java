package com.rubberduck.domain.device.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.device.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Device> findLockedById(Long id);

    Optional<Device> findBySerialNumber(String serialNumber);
}
