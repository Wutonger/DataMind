package com.datamine.analysis.common.service;

import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentFileDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentPreviewDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchRequestDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchResponseDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeUploadRequestDTO;

import java.util.List;

public interface KnowledgeBaseService {

    List<KnowledgeDocumentDTO> uploadDocuments(KnowledgeUploadRequestDTO request);

    List<KnowledgeDocumentDTO> listDocuments(Long connectionId);

    KnowledgeDocumentDTO getDocument(Long documentId);

    KnowledgeDocumentPreviewDTO previewDocument(Long documentId);

    KnowledgeDocumentFileDTO getDocumentFile(Long documentId);

    void deleteDocument(Long documentId);

    KnowledgeDocumentDTO reindexDocument(Long documentId);

    KnowledgeSearchResponseDTO search(KnowledgeSearchRequestDTO request);
}
