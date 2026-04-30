package com.datamine.analysis.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "workflow_timeline",
        indexes = {
                @Index(name = "idx_workflow_timeline_run_order", columnList = "run_id, event_order")
        }
)
public class WorkflowTimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "event_order", nullable = false)
    private Integer eventOrder;

    @Column(name = "time_label", nullable = false, length = 16)
    private String timeLabel;

    @Column(name = "node_id", nullable = false, length = 96)
    private String nodeId;

    @Column(nullable = false, length = 64)
    private String title;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
