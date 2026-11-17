package com.rubberduck.domain.device.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 120)
    private String serialNumber;

    @Column(name = "firmware_version", length = 80)
    private String firmwareVersion;

    @Column(nullable = false, length = 40)
    private String status = "REGISTERED";

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Device create(String serialNumber, String firmwareVersion) {
        Device device = new Device();
        device.setSerialNumber(serialNumber);
        device.setFirmwareVersion(firmwareVersion);
        return device;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        registeredAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
