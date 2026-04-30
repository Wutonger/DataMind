package com.datamine.analysis.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sql_history")
public class SqlHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "`sql`", columnDefinition = "TEXT", nullable = false)
    private String sql;

    @Column(name = "natural_language", columnDefinition = "TEXT")
    private String naturalLanguage;

    @Column(name = "result_preview", columnDefinition = "TEXT")
    private String resultPreview;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
