package com.rubberduck.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.iot.entity.IotEvent;

public interface IotEventRepository extends JpaRepository<IotEvent, Long> {
}
