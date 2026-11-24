package com.rubberduck.domain.document.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rubberduck.domain.document.dto.DocumentResponse;
import com.rubberduck.domain.document.dto.DocumentSearchResponse;
import com.rubberduck.domain.document.dto.DocumentSearchResult;
import com.rubberduck.domain.document.entity.DocumentChunk;
import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.document.repository.DocumentChunkRepository;
import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 10;

    private final LearningDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextExtractionService textExtractionService;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final SparseEmbeddingService sparseEmbeddingService;
    private final LocalDocumentStore documentStore;
    private final CloudDocumentSettings cloudSettings;
    private final CloudDocumentUploadService cloudUpload;
    private final ObjectMapper objectMapper;

    @Value("${document.pipeline-enabled:false}")
    private boolean pipelineEnabled;

    @Transactional
    public DocumentResponse upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        if (cloudSettings.enabled() && !fileName.toLowerCase(Locale.ROOT).endsWith(".docx")
                && !fileName.toLowerCase(Locale.ROOT).endsWith(".pptx")) return cloudUpload.upload(user, file, fileName);
        if (pipelineEnabled && !fileName.toLowerCase(Locale.ROOT).endsWith(".docx")
                && !fileName.toLowerCase(Locale.ROOT).endsWith(".pptx")) return enqueue(user, file, fileName);
        String text = textExtractionService.extract(file);
        LearningDocument document = documentRepository.save(LearningDocument.create(
                user,
                titleFromFileName(fileName),
                fileName,
                file.getContentType(),
                file.getSize(),
                kindFromFileName(fileName)
        ));

        List<String> chunks = documentChunker.split(text);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            chunkRepository.save(DocumentChunk.create(
                    document,
                    i,
                    chunk,
                    embeddingService.toJson(embeddingService.embed(chunk))
            ));
        }

        document.markIndexed();
        return response(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(User user) {
        return documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public void delete(User user, Long documentId) {
        LearningDocument document = getLockedUserDocument(user, documentId);
        try { if (document.getAttemptId() == null) documentStore.deleteArtifacts(document.getSourceKey()); }
        catch (IOException exception) { throw new CustomException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE); }
        chunkRepository.deleteByDocument(document);
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(User user, Long id) { return response(getUserDocument(user, id)); }

    @Transactional
    public DocumentResponse retry(User user, Long id) {
        if (cloudSettings.enabled()) return cloudUpload.retry(user, id);
        var document = getLockedUserDocument(user, id);
        if (!DocumentRetryPolicy.allowed(document, false, pipelineEnabled)) {
            throw new CustomException(ErrorCode.DOCUMENT_RETRY_NOT_ALLOWED);
        }
        document.setIndexingStatus("pending");
        document.setStatus("queued");
        document.setProcessingError(null);
        document.setProcessingToken(null);
        return response(document);
    }

    private DocumentResponse response(LearningDocument document) {
        return DocumentResponse.from(document, DocumentRetryPolicy.allowed(document, cloudSettings.enabled(), pipelineEnabled));
    }

    private DocumentResponse enqueue(User user, MultipartFile file, String fileName) {
        String type = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!List.of("pdf", "txt", "md", "csv", "json").contains(type)) {
            throw new CustomException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
        }
        if (file.getSize() > 20L * 1024 * 1024) throw new CustomException(ErrorCode.INVALID_REQUEST);
        var document = documentRepository.save(LearningDocument.create(user, titleFromFileName(fileName),
                fileName, file.getContentType(), file.getSize(), kindFromFileName(fileName)));
        document.setStatus("queued");
        document.setSourceType(type);
        try {
            document.setSourceKey(documentStore.putSource(user.getId(), document.getId(), file.getBytes()));
        } catch (IOException exception) { throw new CustomException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE); }
        return response(document);
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit) {
        return new DocumentSearchResponse(searchResults(user, query, limit));
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit, List<Long> documentIds) {
        return new DocumentSearchResponse(searchResults(user, query, limit, documentIds));
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResult> searchResults(User user, String query, Integer limit) {
        return searchResults(user, query, limit, null);
    }

    private List<DocumentSearchResult> searchResults(User user, String query, Integer limit, List<Long> documentIds) {
        String normalizedQuery = requireQuery(query);
        int resultLimit = normalizeLimit(limit);
        double[] queryEmbedding = embeddingService.embed(normalizedQuery);
        Map<String, Double> sparseQuery = sparseEmbeddingService.embed(normalizedQuery);
        if (documentIds != null) documentIds.forEach(id -> getUserDocument(user, id));

        return chunkRepository.findByDocument_UserAndDocument_RagEnabledTrue(user).stream()
                .filter(chunk -> "indexed".equals(chunk.getDocument().getIndexingStatus()))
                .filter(chunk -> documentIds == null || documentIds.contains(chunk.getDocument().getId()))
                .map(chunk -> toSearchResult(chunk, queryEmbedding, sparseQuery))
                .filter(result -> result.score() >= 0.08d)
                .sorted(Comparator.comparingDouble(DocumentSearchResult::score).reversed())
                .limit(resultLimit)
                .toList();
    }

    public String toPromptContext(List<DocumentSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.stream()
                .limit(3)
                .map(result -> "[문서 " + result.documentId() + ", 청크 " + result.chunkId() + "] "
                        + result.title() + " / " + (result.headingPath() == null ? "" : result.headingPath())
                        + "\n" + truncate(result.content(), 1400))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private DocumentSearchResult toSearchResult(DocumentChunk chunk, double[] queryEmbedding, Map<String, Double> sparseQuery) {
        return new DocumentSearchResult(
                String.valueOf(chunk.getDocument().getId()),
                String.valueOf(chunk.getId()),
                chunk.getDocument().getTitle(),
                chunk.getContent(),
                SparseEmbeddingService.MODEL.equals(chunk.getEmbeddingModel())
                        ? sparseEmbeddingService.score(sparseQuery, chunk.getEmbeddingJson())
                        : embeddingService.cosine(queryEmbedding, embeddingService.fromJson(chunk.getEmbeddingJson())),
                chunk.getBlockId(),
                chunk.getHeadingPath(),
                pageNumber(chunk)
        );
    }

    private Integer pageNumber(DocumentChunk chunk) {
        String provenance = chunk.getProvenanceJson();
        if (provenance == null || provenance.isBlank()) return null;
        try {
            return DocumentResultValidator.page(objectMapper.readTree(provenance).path("pageNumber"));
        } catch (JacksonException | IllegalArgumentException ignored) {
            // An older chunk can still be cited without a structured page number.
            return null;
        }
    }

    private LearningDocument getUserDocument(User user, Long documentId) {
        if (documentId == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return documentRepository.findByIdAndUser(documentId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private LearningDocument getLockedUserDocument(User user, Long documentId) {
        if (documentId == null) throw new CustomException(ErrorCode.INVALID_REQUEST);
        return documentRepository.findLockedById(documentId)
                .filter(doc -> doc.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private String requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return query.trim();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.txt";
        }
        String safe = fileName.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).trim();
        if (safe.isBlank() || safe.length() > 255) throw new CustomException(ErrorCode.INVALID_REQUEST);
        return safe;
    }

    private String titleFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String title = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        title = title.replace('-', ' ').replace('_', ' ').trim();
        return title.substring(0, Math.min(title.length(), 160));
    }

    private String kindFromFileName(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return "pdf";
        }
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image";
        }
        if (lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")) {
            return "slide";
        }
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return "note";
        }
        return "document";
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
