package com.rubberduck.domain.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.chat.entity.DuckSyncTurn;

public interface DuckSyncTurnRepository extends JpaRepository<DuckSyncTurn, Long> {

    Optional<DuckSyncTurn> findByDeviceSerialAndClientTurnId(String deviceSerial, String clientTurnId);
}
