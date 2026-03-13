package com.liuweiqing.aichat.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"username", "conversation_id"}))
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(columnDefinition = "TEXT")
    private String messagesJson;

    public Conversation(String username, String conversationId, String messagesJson) {
        this.username = username;
        this.conversationId = conversationId;
        this.messagesJson = messagesJson;
    }
}
