package com.datamine.analysis.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "table_metadata")
public class TableMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "`schema`")
    private String schema;

    @Column(columnDefinition = "TEXT")
    private String aiDescription;

    @Column(columnDefinition = "JSON")
    private String fields;

    @Column(columnDefinition = "JSON")
    private String relations;

    private Long rowCount;

    private LocalDateTime analyzedAt;

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
