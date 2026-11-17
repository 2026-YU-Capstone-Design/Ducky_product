package com.rubberduck.domain.device.entity;

import java.time.Instant;

import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "device_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "user_id"})
)
public class DeviceUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String role = "OWNER";

    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    public void requireSerial(String serial) {
        if (!device.getSerialNumber().equals(serial)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    public static DeviceUser link(Device device, User user, String role) {
        DeviceUser deviceUser = new DeviceUser();
        deviceUser.setDevice(device);
        deviceUser.setUser(user);
        deviceUser.setRole(role == null || role.isBlank() ? "OWNER" : role.trim());
        return deviceUser;
    }

    @PrePersist
    void onCreate() {
        linkedAt = Instant.now();
    }
}
