package com.rubberduck.domain.chat.entity;

import java.time.Instant;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 40)
    private String sender;

    @Column(name = "message_text", nullable = false, length = 4000)
    private String messageText;

    @Column(name = "message_type", nullable = false, length = 40)
    private String messageType;

    @Column(name = "input_type", length = 40)
    private String inputType;

    @Column(name = "stt_text", length = 4000)
    private String sttText;

    @Column(name = "tts_text", length = 4000)
    private String ttsText;

    @Column(name = "hint_level")
    private Integer hintLevel;

    @Column(name = "hint_number")
    private Integer hintNumber;

    @Column(name = "stt_success")
    private Boolean sttSuccess;

    @Column(name = "tts_success")
    private Boolean ttsSuccess;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ChatMessage create(
            Conversation conversation,
            String sender,
            String messageText,
            String messageType,
            String inputType,
            int sequenceNo
    ) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setMessageText(messageText);
        message.setMessageType(messageType);
        message.setInputType(inputType);
        message.setSequenceNo(sequenceNo);
        if ("assistant".equals(sender)) {
            message.setTtsText(messageText);
        }
        return message;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
