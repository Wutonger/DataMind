package com.datamine.analysis.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "workflow_steps",
        indexes = {
                @Index(name = "idx_workflow_step_run_order", columnList = "run_id, step_order"),
                @Index(name = "idx_workflow_step_agent", columnList = "agent_id")
        }
)
public class WorkflowStepEntity {

    @Id
    @Column(length = 96)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "agent_id", nullable = false, length = 32)
    private String agentId;

    @Column(nullable = false, length = 64)
    private String owner;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 64)
    private String kind;

    @Column(nullable = false, length = 32)
    private String status;

    @Lob
    @Column(name = "input_summary", columnDefinition = "LONGTEXT")
    private String inputSummary;

    @Lob
    @Column(name = "output_summary", columnDefinition = "LONGTEXT")
    private String outputSummary;

    @Lob
    @Column(name = "tools", columnDefinition = "LONGTEXT")
    private String tools;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        startedAt = startedAt == null ? now : startedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
