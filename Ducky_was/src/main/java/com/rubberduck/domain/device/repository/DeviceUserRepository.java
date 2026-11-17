package com.rubberduck.domain.device.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.device.entity.DeviceUser;
import com.rubberduck.domain.user.entity.User;

public interface DeviceUserRepository extends JpaRepository<DeviceUser, Long> {

    List<DeviceUser> findByUser(User user);

    Optional<DeviceUser> findFirstByDeviceOrderByIdDesc(Device device);

    Optional<DeviceUser> findByDeviceAndUser(Device device, User user);
}
