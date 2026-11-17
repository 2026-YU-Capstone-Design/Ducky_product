package com.rubberduck.domain.device.repository;

import java.util.List;
import java.util.Optional;

import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceUserRepository extends JpaRepository<DeviceUser, Long> {

    @EntityGraph(attributePaths = {"device", "user"})
    Optional<DeviceUser> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = "device")
    List<DeviceUser> findByUserAndTokenHashIsNotNullOrderByIdAsc(User user);

    Optional<DeviceUser> findByDeviceAndUserAndTokenHashIsNotNull(Device device, User user);
}
