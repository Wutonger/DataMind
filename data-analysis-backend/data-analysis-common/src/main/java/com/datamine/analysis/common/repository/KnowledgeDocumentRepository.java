package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByConnectionIdOrderByUpdatedAtDesc(Long connectionId);

    List<KnowledgeDocument> findByConnectionIdAndStatus(Long connectionId, String status);

    Optional<KnowledgeDocument> findByIdAndConnectionId(Long id, Long connectionId);
}
