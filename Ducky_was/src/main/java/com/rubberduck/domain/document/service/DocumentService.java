package com.rubberduck.domain.document.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
import tools.jackson.databind.JsonNode;
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
    private final Bm25SearchService searchScoring;
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
        if (documentIds != null && documentIds.isEmpty()) return List.of();
        if (documentIds != null) documentIds.forEach(id -> getUserDocument(user, id));

        var candidates = documentIds == null
                ? chunkRepository.findSearchableByUser(user)
                : chunkRepository.findSearchableByUserAndDocuments(user, documentIds);
        var scores = searchScoring.scores(candidates, normalizedQuery);
        return candidates.stream()
                .filter(chunk -> scores.containsKey(chunk.getId()))
                .sorted(Comparator.comparingDouble((DocumentChunk chunk) -> scores.get(chunk.getId())).reversed()
                        .thenComparing(DocumentChunk::getId))
                .limit(resultLimit)
                .map(chunk -> toSource(chunk, scores.get(chunk.getId())))
                .toList();
    }

    public String toPromptContext(List<DocumentSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.stream()
                .limit(3)
                .map(result -> "[source_chunk_id: " + result.chunkId() + "] "
                        + result.title() + " / " + (result.headingPath() == null ? "" : result.headingPath())
                        + "\n" + truncate(result.content(), 1400))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResult> sampleReviewSources(User user, List<Long> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) return List.of();
        var owned = selectedIds.stream().distinct().map(id -> getUserDocument(user, id)).toList();
        int limit = 3;
        var documents = owned.stream().filter(doc -> doc.isRagEnabled() && "indexed".equals(doc.getIndexingStatus()))
                .limit(limit).toList();
        var sources = new ArrayList<DocumentSearchResult>(limit);
        for (int docIndex = 0; docIndex < documents.size(); docIndex++) {
            var doc = documents.get(docIndex);
            long count = chunkRepository.countByDocument(doc);
            int share = (limit - sources.size()) / (documents.size() - docIndex);
            int sampleCount = (int) Math.min(share, count);
            for (int index = 0; index < sampleCount; index++) {
                int offset = Math.toIntExact(sampleCount == 1 ? 0 : index * (count - 1) / (sampleCount - 1));
                chunkRepository.findByDocumentOrderByChunkIndexAscIdAsc(doc, PageRequest.of(offset, 1)).stream()
                        .map(chunk -> toSource(chunk, 0.0d)).findFirst().ifPresent(sources::add);
            }
        }
        return List.copyOf(sources);
    }

    private DocumentSearchResult toSource(DocumentChunk chunk, double score) {
        JsonNode provenance = readProvenance(chunk);
        return new DocumentSearchResult(
                String.valueOf(chunk.getDocument().getId()),
                String.valueOf(chunk.getId()),
                chunk.getDocument().getTitle(),
                chunk.getContent(),
                score,
                chunk.getBlockId(),
                chunk.getHeadingPath(),
                pageNumber(provenance),
                blockIds(provenance, chunk.getBlockId())
        );
    }

    private JsonNode readProvenance(DocumentChunk chunk) {
        String provenance = chunk.getProvenanceJson();
        if (provenance == null || provenance.isBlank()) return null;
        try {
            return objectMapper.readTree(provenance);
        } catch (JacksonException ignored) {
            return null;
        }
    }

    private Integer pageNumber(JsonNode provenance) {
        if (provenance == null) return null;
        try {
            return DocumentResultValidator.page(provenance.path("pageNumber"));
        } catch (JacksonException | IllegalArgumentException ignored) {
            // An older chunk can still be cited without a structured page number.
            return null;
        }
    }

    private List<String> blockIds(JsonNode provenance, String primaryBlockId) {
        if (provenance == null || primaryBlockId == null) return List.of();
        JsonNode ids = provenance.path("blockIds");
        if (!ids.isArray() || !primaryBlockId.equals(ids.path(0).asText())) return List.of();
        List<String> result = new ArrayList<>(ids.size());
        for (JsonNode id : ids) {
            if (!id.isString() || id.asText().isBlank()) return List.of();
            result.add(id.asText());
        }
        return result;
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
