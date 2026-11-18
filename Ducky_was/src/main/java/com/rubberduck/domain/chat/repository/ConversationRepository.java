package com.rubberduck.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.user.entity.User;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    interface ConversationId {
        Long getId();
    }

    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

    Optional<ConversationId> findFirstByUserAndDeviceAndStatusOrderByUpdatedAtDesc(User user, Device device, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversation> findLockedById(Long id);
}
