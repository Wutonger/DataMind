package com.datamine.analysis.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(columnDefinition = "JSON")
    private String messages;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "compressed_at")
    private LocalDateTime compressedAt;

    @Column(name = "compressed_messages", columnDefinition = "JSON")
    private String compressedMessages;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
