package com.datamine.analysis.common.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer chunkIndex;

    @Column(columnDefinition = "JSON")
    private String embedding;

    @Column(columnDefinition = "JSON")
    private String metadata;
}
