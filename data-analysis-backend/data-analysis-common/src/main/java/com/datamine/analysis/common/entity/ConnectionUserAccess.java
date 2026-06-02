package com.datamine.analysis.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "connection_user_access",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_connection_user_access", columnNames = {"connection_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_connection_access_user", columnList = "user_id"),
                @Index(name = "idx_connection_access_connection", columnList = "connection_id")
        }
)
public class ConnectionUserAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
