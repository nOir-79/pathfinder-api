package com.amplifiers.pathfinder.entity.chat_message;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "chat_message")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue
    private Integer id;

    private String chatId;
    private Integer senderId;
    private Integer receiverId;
    private String message;
    private OffsetDateTime timeStamp;
    private boolean read; // by the receiver.

    private String senderFullName;
    private String receiverFullName;
}
