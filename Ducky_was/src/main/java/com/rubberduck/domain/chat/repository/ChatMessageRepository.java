package com.rubberduck.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderBySequenceNoAsc(Conversation conversation);

    List<ChatMessage> findByConversationAndSenderOrderBySequenceNoDesc(Conversation conversation, String sender);

    void deleteByConversation(Conversation conversation);
}
