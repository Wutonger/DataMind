package com.datamine.analysis.core.service;

import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentChunkPreviewDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentFileDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeDocumentPreviewDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchRequestDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchResponseDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeUploadFileDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeUploadRequestDTO;
import com.datamine.analysis.common.entity.DocumentChunk;
import com.datamine.analysis.common.entity.KnowledgeDocument;
import com.datamine.analysis.common.repository.ConnectionRepository;
import com.datamine.analysis.common.repository.DocumentChunkRepository;
import com.datamine.analysis.common.repository.KnowledgeDocumentRepository;
import com.datamine.analysis.common.service.KnowledgeBaseService;
import com.datamine.analysis.core.chat.EmbeddingModelFactory;
import com.datamine.analysis.core.util.ParagraphAwareCharacterTextSplitter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "txt", "md", "markdown", "docx");
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private static final int RETRIEVAL_TOP_K = 3;
    private static final double RETRIEVAL_THRESHOLD = 0.5D;
    private static final Path STORAGE_ROOT = Path.of("storage", "knowledge");
    private static final ParagraphAwareCharacterTextSplitter KNOWLEDGE_TEXT_SPLITTER =
            new ParagraphAwareCharacterTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP);

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ConnectionRepository connectionRepository;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<KnowledgeDocumentDTO> uploadDocuments(KnowledgeUploadRequestDTO request) {
        validateConnection(request.connectionId());
        List<KnowledgeUploadFileDTO> files = request.files() == null ? List.of() : request.files();
        if (files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }

        List<KnowledgeDocumentDTO> results = new ArrayList<>();
        for (KnowledgeUploadFileDTO file : files) {
            results.add(uploadSingleDocument(request.connectionId(), file));
        }
        return results;
    }

    @Override
    public List<KnowledgeDocumentDTO> listDocuments(Long connectionId) {
        validateConnection(connectionId);
        return knowledgeDocumentRepository.findByConnectionIdOrderByUpdatedAtDesc(connectionId).stream()
                .map(this::toDocumentDto)
                .toList();
    }

    @Override
    public KnowledgeDocumentDTO getDocument(Long documentId) {
        return toDocumentDto(getDocumentEntity(documentId));
    }

    @Override
    public KnowledgeDocumentPreviewDTO previewDocument(Long documentId) {
        KnowledgeDocument document = getDocumentEntity(documentId);
        List<KnowledgeDocumentChunkPreviewDTO> chunks = loadPreviewChunks(document);
        return new KnowledgeDocumentPreviewDTO(toDocumentDto(document), chunks);
    }

    @Override
    public KnowledgeDocumentFileDTO getDocumentFile(Long documentId) {
        KnowledgeDocument document = getDocumentEntity(documentId);
        Path path = resolveExistingPath(document);
        return new KnowledgeDocumentFileDTO(document.getName(), resolveContentType(document.getType()), path);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = getDocumentEntity(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
        knowledgeDocumentRepository.delete(document);
        deleteSourceDirectory(document.getConnectionId(), documentId);
    }

    @Override
    @Transactional
    public KnowledgeDocumentDTO reindexDocument(Long documentId) {
        KnowledgeDocument document = getDocumentEntity(documentId);
        Path path = resolveExistingPath(document);
        reindexExistingDocument(document, path);
        return toDocumentDto(document);
    }

    @Override
    public KnowledgeSearchResponseDTO search(KnowledgeSearchRequestDTO request) {
        Long connectionId = request.connectionId();
        validateConnection(connectionId);
        String question = request.query();
        if (!StringUtils.hasText(question)) {
            logKnowledgeSearch(question, false, 0, "empty_query");
            return new KnowledgeSearchResponseDTO("", List.of());
        }

        List<KnowledgeDocument> readyDocuments = knowledgeDocumentRepository
                .findByConnectionIdAndStatus(connectionId, "ready");
        if (readyDocuments.isEmpty()) {
            logKnowledgeSearch(question, false, 0, "no_ready_documents");
            return new KnowledgeSearchResponseDTO("当前连接下还没有可用的知识库文档。", List.of());
        }

        Map<Long, KnowledgeDocument> documentMap = readyDocuments.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document));
        List<DocumentChunk> candidateChunks = documentChunkRepository.findByConnectionId(connectionId).stream()
                .filter(chunk -> documentMap.containsKey(chunk.getDocumentId()))
                .toList();

        if (candidateChunks.isEmpty()) {
            logKnowledgeSearch(question, false, 0, "no_candidate_chunks");
            return new KnowledgeSearchResponseDTO("当前连接下还没有可检索的知识分块。", List.of());
        }

        float[] queryEmbedding = embeddingModelFactory.getEmbeddingModel().embed(question);
        List<KnowledgeCitationDTO> citations = candidateChunks.stream()
                .map(chunk -> scoreChunk(chunk, documentMap.get(chunk.getDocumentId()), queryEmbedding))
                .filter(Objects::nonNull)
                .filter(scored -> scored.score() >= RETRIEVAL_THRESHOLD)
                .collect(Collectors.toMap(
                        scored -> scored.citation().documentId(),
                        scored -> scored,
                        (left, right) -> left.score() >= right.score() ? left : right,
                        LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(ScoredCitation::score).reversed())
                .limit(RETRIEVAL_TOP_K)
                .map(ScoredCitation::citation)
                .toList();

        logKnowledgeSearch(question, !citations.isEmpty(), citations.size(), citations.isEmpty() ? "no_match" : "matched");
        return new KnowledgeSearchResponseDTO(buildSearchSummary(citations), citations);
    }

    private KnowledgeDocumentDTO uploadSingleDocument(Long connectionId,
                                                      KnowledgeUploadFileDTO file) {
        String originalFilename = StringUtils.hasText(file.originalFilename())
                ? file.originalFilename().trim()
                : "document";
        String extension = resolveSupportedExtension(originalFilename);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setConnectionId(connectionId);
        document.setName(originalFilename);
        document.setType(normalizeType(extension));
        document.setStatus("pending");
        document.setTotalChunks(0);
        document.setErrorMessage(null);
        document = knowledgeDocumentRepository.save(document);

        Path sourcePath = storagePath(connectionId, document.getId(), extension);
        try {
            storeSourceFile(sourcePath, file.content());
            document.setFilePath(sourcePath.toString());
            knowledgeDocumentRepository.save(document);
            reindexExistingDocument(document, sourcePath);
        } catch (Exception e) {
            markDocumentError(document, e);
        }

        return toDocumentDto(document);
    }

    private void reindexExistingDocument(KnowledgeDocument document, Path sourcePath) {
        document.setStatus("embedding");
        document.setErrorMessage(null);
        document.setTotalChunks(0);
        knowledgeDocumentRepository.save(document);
        documentChunkRepository.deleteByDocumentId(document.getId());

        try {
            List<Document> rawDocuments = readSourceDocuments(sourcePath, document.getType());
            List<Document> chunkDocuments = splitDocuments(rawDocuments, document.getType());
            saveChunks(document, chunkDocuments);
            document.setStatus("ready");
            document.setTotalChunks(chunkDocuments.size());
            document.setErrorMessage(null);
            document.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentRepository.save(document);
            log.info("Knowledge document indexed successfully. documentId={}, chunks={}",
                    document.getId(), chunkDocuments.size());
        } catch (Exception e) {
            markDocumentError(document, e);
        }
    }

    private List<Document> readSourceDocuments(Path sourcePath, String type) {
        DocumentReader reader = createReader(sourcePath, type);
        List<Document> rawDocuments = reader.get();
        if (rawDocuments == null || rawDocuments.isEmpty()) {
            throw new IllegalStateException("No readable text extracted from document");
        }

        List<Document> normalized = new ArrayList<>(rawDocuments.size());
        for (Document rawDocument : rawDocuments) {
            if (!StringUtils.hasText(rawDocument.getText())) {
                continue;
            }
            Map<String, Object> metadata = sanitizeMetadata(rawDocument.getMetadata());
            metadata.put("page", resolvePage(metadata));
            metadata.put("sectionTitle", resolveSectionTitle(metadata));
            metadata.put("sourceType", type);
            normalized.add(new Document(rawDocument.getText(), sanitizeMetadata(metadata)));
        }

        if (normalized.isEmpty()) {
            throw new IllegalStateException("No readable text extracted from document");
        }
        return normalized;
    }

    private DocumentReader createReader(Path sourcePath, String type) {
        FileSystemResource resource = new FileSystemResource(sourcePath);
        return switch (normalizeType(type)) {
            case "pdf" -> new PagePdfDocumentReader(resource);
            case "txt" -> new TextReader(resource);
            case "md" -> new TextReader(resource);
            case "docx" -> new TikaDocumentReader(resource);
            default -> throw new IllegalArgumentException("Unsupported document type: " + type);
        };
    }

    private List<KnowledgeDocumentChunkPreviewDTO> loadPreviewChunks(KnowledgeDocument document) {
        if ("md".equals(normalizeType(document.getType()))) {
            return buildMarkdownPreviewChunks(document);
        }

        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId())
                .stream()
                .map(chunk -> new KnowledgeDocumentChunkPreviewDTO(
                        chunk.getChunkIndex(),
                        defaultString(chunk.getContent()),
                        parseJsonMap(chunk.getMetadata())))
                .toList();
    }

    private List<KnowledgeDocumentChunkPreviewDTO> buildMarkdownPreviewChunks(KnowledgeDocument document) {
        try {
            Path sourcePath = resolveExistingPath(document);
            List<Document> rawDocuments = readSourceDocuments(sourcePath, document.getType());
            List<Document> chunkDocuments = splitDocuments(rawDocuments, document.getType());
            List<KnowledgeDocumentChunkPreviewDTO> previewChunks = new ArrayList<>(chunkDocuments.size());
            for (int index = 0; index < chunkDocuments.size(); index++) {
                Document chunkDocument = chunkDocuments.get(index);
                previewChunks.add(new KnowledgeDocumentChunkPreviewDTO(
                        index,
                        defaultString(chunkDocument.getText()),
                        normalizeChunkMetadata(chunkDocument.getMetadata())));
            }
            return previewChunks;
        } catch (Exception ex) {
            log.warn("Failed to rebuild markdown preview from source. documentId={}", document.getId(), ex);
            return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId())
                    .stream()
                    .map(chunk -> new KnowledgeDocumentChunkPreviewDTO(
                            chunk.getChunkIndex(),
                            defaultString(chunk.getContent()),
                            parseJsonMap(chunk.getMetadata())))
                    .toList();
        }
    }

    private List<Document> splitDocuments(List<Document> rawDocuments, String type) {
        List<Document> normalized = new ArrayList<>();
        for (Document rawDocument : rawDocuments) {
            String sourceText = defaultString(rawDocument.getText());
            if (!StringUtils.hasText(sourceText)) {
                continue;
            }

            Map<String, Object> metadata = sanitizeMetadata(rawDocument.getMetadata());
            metadata.put("page", resolvePage(metadata));
            metadata.put("sectionTitle", resolveSectionTitle(metadata));
            metadata.put("sourceType", type);

            for (String chunkText : KNOWLEDGE_TEXT_SPLITTER.splitToTextChunks(sourceText)) {
                if (StringUtils.hasText(chunkText)) {
                    normalized.add(new Document(chunkText, sanitizeMetadata(metadata)));
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("No chunk generated from document");
        }
        return normalized;
    }

    private void saveChunks(KnowledgeDocument document, List<Document> chunkDocuments) {
        EmbeddingModel embeddingModel = embeddingModelFactory.getEmbeddingModel();
        List<DocumentChunk> chunks = new ArrayList<>(chunkDocuments.size());
        for (int index = 0; index < chunkDocuments.size(); index++) {
            Document splitDocument = chunkDocuments.get(index);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setConnectionId(document.getConnectionId());
            chunk.setChunkIndex(index);
            chunk.setContent(splitDocument.getText());
            chunk.setMetadata(writeJson(normalizeChunkMetadata(splitDocument.getMetadata())));
            chunk.setEmbedding(writeJson(toDoubleList(embeddingModel.embed(splitDocument.getText()))));
            chunks.add(chunk);
        }
        documentChunkRepository.saveAll(chunks);
    }

    private ScoredCitation scoreChunk(DocumentChunk chunk,
                                      KnowledgeDocument document,
                                      float[] queryEmbedding) {
        List<Double> embedding = parseJsonDoubleList(chunk.getEmbedding());
        if (embedding.isEmpty()) {
            return null;
        }

        double cosine = cosineSimilarity(queryEmbedding, embedding);
        if (Double.isNaN(cosine)) {
            return null;
        }

        Map<String, Object> metadata = parseJsonMap(chunk.getMetadata());
        return new ScoredCitation(cosine, new KnowledgeCitationDTO(
                chunk.getDocumentId(),
                document.getName(),
                chunk.getChunkIndex(),
                defaultString(chunk.getContent()).trim(),
                roundScore(cosine),
                metadata));
    }

    private String buildSearchSummary(List<KnowledgeCitationDTO> citations) {
        if (citations.isEmpty()) {
            return "没有找到和当前问题足够相关的知识片段。";
        }

        return citations.stream()
                .limit(3)
                .map(citation -> citation.documentName() + " · 片段 " + (citation.chunkIndex() + 1))
                .collect(Collectors.joining("\n"));
    }

    private void logKnowledgeSearch(String question, boolean matched, int hitCount, String reason) {
        log.info("Knowledge search. question=\"{}\", matched={}, hitCount={}, reason={}",
                normalizeQuestionForLog(question), matched, hitCount, reason);
    }

    private String normalizeQuestionForLog(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        return question.replaceAll("\\s+", " ").trim();
    }

    private KnowledgeDocumentDTO toDocumentDto(KnowledgeDocument document) {
        return new KnowledgeDocumentDTO(
                document.getId(),
                document.getConnectionId(),
                document.getName(),
                document.getType(),
                document.getStatus(),
                document.getTotalChunks(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    private void validateConnection(Long connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("connectionId is required");
        }
        if (!connectionRepository.existsById(connectionId)) {
            throw new IllegalArgumentException("Connection not found: " + connectionId);
        }
    }

    private KnowledgeDocument getDocumentEntity(Long documentId) {
        return knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found: " + documentId));
    }

    private void storeSourceFile(Path targetPath, byte[] content) throws IOException {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, content);
    }

    private Path storagePath(Long connectionId, Long documentId, String extension) {
        return STORAGE_ROOT
                .resolve(String.valueOf(connectionId))
                .resolve(String.valueOf(documentId))
                .resolve("source." + extension.toLowerCase(Locale.ROOT));
    }

    private Path resolveExistingPath(KnowledgeDocument document) {
        if (!StringUtils.hasText(document.getFilePath())) {
            throw new IllegalStateException("Knowledge document source file is missing");
        }

        Path path = Path.of(document.getFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("Knowledge document source file does not exist");
        }
        return path;
    }

    private void deleteSourceDirectory(Long connectionId, Long documentId) {
        Path directory = STORAGE_ROOT
                .resolve(String.valueOf(connectionId))
                .resolve(String.valueOf(documentId));
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete knowledge storage path: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete knowledge storage directory: {}", directory, e);
        }
    }

    private void markDocumentError(KnowledgeDocument document, Exception error) {
        document.setStatus("error");
        document.setTotalChunks(0);
        document.setErrorMessage(resolveErrorMessage(error));
        knowledgeDocumentRepository.save(document);
        log.error("Knowledge document indexing failed. documentId={}", document.getId(), error);
    }

    private String resolveSupportedExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index >= filename.length() - 1) {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }
        String extension = filename.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type: " + extension);
        }
        return extension;
    }

    private String normalizeType(String extensionOrType) {
        String normalized = defaultString(extensionOrType).toLowerCase(Locale.ROOT);
        if ("markdown".equals(normalized)) {
            return "md";
        }
        return normalized;
    }

    private Integer resolvePage(Map<String, Object> metadata) {
        for (String key : List.of("page", "pageNumber", "page_number", "page-number")) {
            Object value = metadata.get(key);
            Integer page = parseInteger(value);
            if (page != null) {
                return page;
            }
        }
        for (String key : List.of("startPageNumber", "start_page_number", "start-page-number")) {
            Object value = metadata.get(key);
            Integer page = parseInteger(value);
            if (page != null) {
                return page;
            }
        }
        return null;
    }

    private String resolveSectionTitle(Map<String, Object> metadata) {
        for (String key : List.of("sectionTitle", "section_title", "section-heading", "title", "heading")) {
            Object value = metadata.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private Map<String, Object> normalizeChunkMetadata(Map<String, Object> metadata) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("page", resolvePage(metadata));
        normalized.put("sectionTitle", resolveSectionTitle(metadata));
        normalized.put("sourceType", metadata.get("sourceType"));
        return sanitizeMetadata(normalized);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return sanitized;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize knowledge data", e);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse JSON map", e);
            return new LinkedHashMap<>();
        }
    }

    private List<Double> parseJsonDoubleList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse embedding JSON", e);
            return List.of();
        }
    }

    private List<Double> toDoubleList(float[] embedding) {
        List<Double> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add((double) value);
        }
        return values;
    }

    private double cosineSimilarity(float[] queryEmbedding, List<Double> chunkEmbedding) {
        int dimensions = Math.min(queryEmbedding.length, chunkEmbedding.size());
        if (dimensions == 0) {
            return Double.NaN;
        }

        double dot = 0D;
        double queryNorm = 0D;
        double chunkNorm = 0D;
        for (int index = 0; index < dimensions; index++) {
            double queryValue = queryEmbedding[index];
            double chunkValue = chunkEmbedding.get(index);
            dot += queryValue * chunkValue;
            queryNorm += queryValue * queryValue;
            chunkNorm += chunkValue * chunkValue;
        }

        if (queryNorm == 0D || chunkNorm == 0D) {
            return Double.NaN;
        }
        return dot / (Math.sqrt(queryNorm) * Math.sqrt(chunkNorm));
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = defaultString(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim() + "...";
    }

    private Double roundScore(double score) {
        return Math.round(score * 10000D) / 10000D;
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveContentType(String type) {
        return switch (normalizeType(type)) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private String resolveErrorMessage(Exception error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : "Knowledge document processing failed";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record ScoredCitation(double score, KnowledgeCitationDTO citation) {
    }
}
