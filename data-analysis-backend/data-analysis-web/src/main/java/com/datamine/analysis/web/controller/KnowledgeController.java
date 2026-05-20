package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentFileDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentPreviewDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchRequestDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchResponseDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeUploadFileDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeUploadRequestDTO;
import com.datamine.analysis.common.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocuments(@RequestParam Long connectionId,
                                             @RequestPart("files") MultipartFile[] files) {
        try {
            List<KnowledgeUploadFileDTO> uploadFiles = new java.util.ArrayList<>(files.length);
            for (MultipartFile file : files) {
                uploadFiles.add(toUploadFile(file));
            }
            List<KnowledgeDocumentDTO> result = knowledgeBaseService.uploadDocuments(new KnowledgeUploadRequestDTO(connectionId, uploadFiles));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(@RequestParam Long connectionId) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.listDocuments(connectionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.getDocument(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<?> previewDocument(@PathVariable Long id) {
        try {
            KnowledgeDocumentPreviewDTO preview = knowledgeBaseService.previewDocument(id);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable Long id) {
        try {
            KnowledgeDocumentFileDTO file = knowledgeBaseService.getDocumentFile(id);
            String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8);
            InputStreamResource resource = new InputStreamResource(java.nio.file.Files.newInputStream(file.path()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        try {
            knowledgeBaseService.deleteDocument(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/documents/{id}/reindex")
    public ResponseEntity<?> reindexDocument(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.reindexDocument(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@org.springframework.web.bind.annotation.RequestBody KnowledgeSearchRequestDTO request) {
        try {
            KnowledgeSearchResponseDTO result = knowledgeBaseService.search(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private KnowledgeUploadFileDTO toUploadFile(MultipartFile file) throws IOException {
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "document";
        return new KnowledgeUploadFileDTO(fileName, file.getContentType(), file.getBytes());
    }
}
