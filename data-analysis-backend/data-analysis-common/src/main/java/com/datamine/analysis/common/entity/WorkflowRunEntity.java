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
        name = "workflow_runs",
        indexes = {
                @Index(name = "idx_workflow_run_conn", columnList = "connection_id"),
                @Index(name = "idx_workflow_run_scene_conn_started", columnList = "scene, connection_id, started_at"),
                @Index(name = "idx_workflow_run_scene_started", columnList = "scene, started_at"),
                @Index(name = "idx_workflow_run_status", columnList = "status")
        }
)
public class WorkflowRunEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String scene;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "route_mode", length = 64)
    private String routeMode;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Lob
    @Column(name = "final_path", columnDefinition = "LONGTEXT")
    private String finalPath;

    @Lob
    @Column(name = "used_agents", columnDefinition = "LONGTEXT")
    private String usedAgents;

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
