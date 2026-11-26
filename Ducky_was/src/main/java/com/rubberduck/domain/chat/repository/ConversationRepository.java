package com.rubberduck.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.device.entity.Device;
import com.rubberduck.domain.user.entity.User;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    interface ConversationId {
        Long getId();
    }

    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

    @Query("""
            select c.id as id from Conversation c
            where c.user = :user and c.status = :status and (c.device = :device or c.device is null)
            order by c.updatedAt desc, c.id desc
            """)
    List<ConversationId> findLatestAssignable(User user, Device device, String status, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversation> findLockedById(Long id);
}
