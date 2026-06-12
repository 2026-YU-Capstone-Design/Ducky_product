package com.rubberduck.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.user.entity.User;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

    java.util.Optional<Conversation> findFirstByUserAndStatusOrderByUpdatedAtDesc(User user, String status);
}
