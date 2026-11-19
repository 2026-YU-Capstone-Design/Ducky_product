package com.rubberduck.domain.document.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Objects;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 10;
    private static final int MAX_CONTEXT_PER_SOURCE = 2800;
    private final ObjectMapper provenanceMapper = new ObjectMapper();

    private final LearningDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextExtractionService textExtractionService;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final SparseEmbeddingService sparseEmbeddingService;
    private final Bm25SearchService bm25SearchService;
    private final LocalDocumentStore documentStore;

    @Value("${document.pipeline-enabled:false}")
    private boolean pipelineEnabled;

    @Value("${document.retrieval-mode:lexical}")
    private String defaultRetrievalMode;

    @Transactional
    public DocumentResponse upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
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
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(User user) {
        return documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public void delete(User user, Long documentId) {
        LearningDocument document = getLockedUserDocument(user, documentId);
        try { documentStore.deleteArtifacts(document.getSourceKey()); }
        catch (IOException exception) { throw new CustomException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE); }
        chunkRepository.deleteByDocument(document);
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(User user, Long id) { return DocumentResponse.from(getUserDocument(user, id)); }

    @Transactional
    public DocumentResponse retry(User user, Long id) {
        var document = getLockedUserDocument(user, id);
        if (!pipelineEnabled || !"failed".equals(document.getIndexingStatus()) || document.getSourceKey() == null) {
            throw new CustomException(ErrorCode.DOCUMENT_RETRY_NOT_ALLOWED);
        }
        document.setIndexingStatus("pending");
        document.setStatus("queued");
        document.setProcessingError(null);
        document.setProcessingToken(null);
        return DocumentResponse.from(document);
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
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit) {
        return search(user, query, limit, null, null);
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit, List<Long> documentIds) {
        return search(user, query, limit, documentIds, null);
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit, List<Long> documentIds, String mode) {
        String resolvedMode = resolveRetrievalMode(mode);
        return new DocumentSearchResponse(searchResults(user, query, limit, documentIds, resolvedMode), resolvedMode);
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResult> searchResults(User user, String query, Integer limit) {
        return searchResults(user, query, limit, null, resolveRetrievalMode(null));
    }

    private List<DocumentSearchResult> searchResults(User user, String query, Integer limit, List<Long> documentIds, String mode) {
        String normalizedQuery = requireQuery(query);
        int resultLimit = normalizeLimit(limit);
        if (documentIds != null) documentIds.forEach(id -> getUserDocument(user, id));
        List<DocumentChunk> corpus = chunkRepository.findByDocument_UserAndDocument_RagEnabledTrue(user).stream()
                .filter(chunk -> "indexed".equals(chunk.getDocument().getIndexingStatus()))
                .toList();
        if ("bm25".equals(mode)) {
            Map<Long, Double> scores = bm25SearchService.scores(corpus, normalizedQuery);
            return corpus.stream()
                    .filter(chunk -> documentIds == null || documentIds.contains(chunk.getDocument().getId()))
                    .filter(chunk -> scores.getOrDefault(chunk.getId(), 0.0) > 0)
                    .map(chunk -> toSearchResult(chunk, scores.get(chunk.getId())))
                    .sorted(searchOrder())
                    .limit(resultLimit)
                    .toList();
        }
        double[] queryEmbedding = embeddingService.embed(normalizedQuery);
        Map<String, Double> sparseQuery = sparseEmbeddingService.embed(normalizedQuery);
        return corpus.stream()
                .filter(chunk -> documentIds == null || documentIds.contains(chunk.getDocument().getId()))
                .map(chunk -> toSearchResult(chunk, queryEmbedding, sparseQuery))
                .filter(result -> result.score() >= 0.08d)
                .sorted(searchOrder())
                .limit(resultLimit)
                .toList();
    }

    private String resolveRetrievalMode(String requested) {
        String mode = requested == null || requested.isBlank() ? defaultRetrievalMode : requested;
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("lexical", "bm25").contains(mode)) throw new CustomException(ErrorCode.INVALID_REQUEST);
        return mode;
    }

    private Comparator<DocumentSearchResult> searchOrder() {
        return Comparator.comparingDouble(DocumentSearchResult::score).reversed()
                .thenComparingLong(result -> Long.parseLong(result.chunkId()));
    }

    /** Expand citations only inside the authenticated owner's indexed document
     * and PDF page (or Markdown heading). Raw search ranks remain unchanged. */
    @Transactional(readOnly = true)
    public List<DocumentSearchResult> expandContext(User user, List<DocumentSearchResult> matches) {
        if (matches == null || matches.isEmpty()) return List.of();
        var corpus = chunkRepository.findByDocument_UserAndDocument_RagEnabledTrue(user).stream()
                .filter(chunk -> "indexed".equals(chunk.getDocument().getIndexingStatus())).toList();
        var used = new HashSet<Long>();
        var expanded = new ArrayList<DocumentSearchResult>();
        for (var match : matches.stream().limit(3).toList()) {
            var anchor = corpus.stream().filter(c -> c.getId().toString().equals(match.chunkId())
                    && c.getDocument().getId().toString().equals(match.documentId())).findFirst().orElse(null);
            if (anchor == null || used.contains(anchor.getId())) continue;
            var selected = new ArrayList<DocumentChunk>();
            selected.add(anchor);
            int length = anchor.getContent().length();
            for (int offset : new int[]{-1, 1}) {
                var neighbor = corpus.stream().filter(c -> c.getDocument().getId().equals(anchor.getDocument().getId())
                        && c.getChunkIndex() == anchor.getChunkIndex() + offset
                        && !used.contains(c.getId()) && sameContext(anchor, c)).findFirst().orElse(null);
                if (neighbor != null && length + 2 + neighbor.getContent().length() <= MAX_CONTEXT_PER_SOURCE) {
                    selected.add(neighbor);
                    length += 2 + neighbor.getContent().length();
                }
            }
            selected.sort(Comparator.comparingInt(DocumentChunk::getChunkIndex));
            var blocks = new LinkedHashSet<String>();
            var ids = new ArrayList<String>();
            var texts = new ArrayList<String>();
            for (var chunk : selected) {
                blocks.addAll(toSearchResult(chunk, 0).blockIds());
                ids.add(chunk.getId().toString());
                texts.add(chunk.getContent());
                used.add(chunk.getId());
            }
            expanded.add(new DocumentSearchResult(match.documentId(), match.chunkId(), match.title(),
                    String.join("\n\n", texts), match.score(), match.blockId(), match.headingPath(),
                    List.copyOf(blocks), pageNumber(anchor), ids));
        }
        return expanded;
    }

    private boolean sameContext(DocumentChunk left, DocumentChunk right) {
        Integer page = pageNumber(left);
        Integer other = pageNumber(right);
        if (page != null || other != null) return Objects.equals(page, other);
        return Objects.equals(left.getHeadingPath(), right.getHeadingPath());
    }

    private JsonNode provenance(DocumentChunk chunk) {
        try {
            if (chunk.getProvenanceJson() != null) {
                var parsed = provenanceMapper.readTree(chunk.getProvenanceJson());
                if (parsed != null && parsed.isObject()) return parsed;
            }
        } catch (RuntimeException ignored) {
            // Legacy chunks have no structured provenance; keep citation compatibility.
        }
        return provenanceMapper.createObjectNode();
    }

    private Integer pageNumber(DocumentChunk chunk) {
        var page = provenance(chunk).path("pageNumber");
        return page.isIntegralNumber() && page.asInt() > 0 ? page.asInt() : null;
    }

    public String toPromptContext(List<DocumentSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.stream()
                .limit(3)
                .map(result -> "[문서 " + result.documentId() + ", 청크 " + result.chunkId() + "] "
                        + result.title() + " / " + (result.headingPath() == null ? "" : result.headingPath())
                        + (result.pageNumber() == null ? "" : " / PDF " + result.pageNumber() + "쪽")
                        + "\n" + truncate(result.content(), MAX_CONTEXT_PER_SOURCE))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private DocumentSearchResult toSearchResult(DocumentChunk chunk, double[] queryEmbedding, Map<String, Double> sparseQuery) {
        double score = SparseEmbeddingService.MODEL.equals(chunk.getEmbeddingModel())
                ? sparseEmbeddingService.score(sparseQuery, chunk.getEmbeddingJson())
                : embeddingService.cosine(queryEmbedding, embeddingService.fromJson(chunk.getEmbeddingJson()));
        return toSearchResult(chunk, score);
    }

    private DocumentSearchResult toSearchResult(DocumentChunk chunk, double score) {
        var node = provenance(chunk);
        var blockIds = new ArrayList<String>();
        if (node.path("blockIds").isArray()) node.path("blockIds").forEach(id -> {
            if (!id.asText().isBlank()) blockIds.add(id.asText());
        });
        if (blockIds.isEmpty() && chunk.getBlockId() != null) blockIds.add(chunk.getBlockId());
        return new DocumentSearchResult(
                String.valueOf(chunk.getDocument().getId()),
                String.valueOf(chunk.getId()),
                chunk.getDocument().getTitle(),
                chunk.getContent(),
                score,
                chunk.getBlockId(),
                chunk.getHeadingPath(),
                blockIds,
                pageNumber(chunk),
                List.of(chunk.getId().toString())
        );
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
