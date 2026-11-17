package com.rubberduck.domain.document.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
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

    @Transactional
    public DocumentResponse upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
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
        LearningDocument document = getUserDocument(user, documentId);
        chunkRepository.deleteByDocument(document);
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentSearchResponse search(User user, String query, Integer limit) {
        return new DocumentSearchResponse(searchResults(user, query, limit));
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResult> searchResults(User user, String query, Integer limit) {
        String normalizedQuery = requireQuery(query);
        int resultLimit = normalizeLimit(limit);
        double[] queryEmbedding = embeddingService.embed(normalizedQuery);

        return chunkRepository.findByDocument_UserAndDocument_RagEnabledTrue(user).stream()
                .map(chunk -> toSearchResult(chunk, queryEmbedding))
                .filter(result -> result.score() > 0.0d)
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
                .map(result -> result.title() + ": " + truncate(result.content(), 240))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private DocumentSearchResult toSearchResult(DocumentChunk chunk, double[] queryEmbedding) {
        return new DocumentSearchResult(
                String.valueOf(chunk.getDocument().getId()),
                String.valueOf(chunk.getId()),
                chunk.getDocument().getTitle(),
                chunk.getContent(),
                embeddingService.cosine(queryEmbedding, embeddingService.fromJson(chunk.getEmbeddingJson()))
        );
    }

    private LearningDocument getUserDocument(User user, Long documentId) {
        if (documentId == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return documentRepository.findByIdAndUser(documentId, user)
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
        return fileName.trim();
    }

    private String titleFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String title = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return title.replace('-', ' ').replace('_', ' ').trim();
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
