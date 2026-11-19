package com.rubberduck.domain.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.rubberduck.domain.document.entity.DocumentChunk;
import com.rubberduck.domain.document.repository.DocumentChunkRepository;
import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class DocumentProcessingService {
    private final LearningDocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final LocalDocumentStore store;
    private final DocumentProcessorClient processor;
    private final SparseEmbeddingService embeddings;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final long leaseSeconds;
    private final CloudDocumentSettings cloudSettings;

    public DocumentProcessingService(LearningDocumentRepository documents, DocumentChunkRepository chunks,
            LocalDocumentStore store, DocumentProcessorClient processor, SparseEmbeddingService embeddings,
            PlatformTransactionManager manager, CloudDocumentSettings cloudSettings,
            @Value("${document.pipeline-enabled:false}") boolean enabled,
            @Value("${document.processing-timeout-seconds:300}") long timeout) {
        this.cloudSettings = cloudSettings;
        this.documents = documents; this.chunks = chunks; this.store = store;
        this.processor = processor; this.embeddings = embeddings;
        this.transaction = new TransactionTemplate(manager);
        this.enabled = enabled; this.leaseSeconds = timeout + 120;
    }

    record Job(Long id, String token, String sourceKey, String sourceType) {}

    @Scheduled(fixedDelayString = "${document.poll-interval-ms:2000}")
    public void poll() {
        if (!enabled || cloudSettings.enabled()) return;
        // A crashed process leaves a durable job, not a permanently spinning UI.
        documents.findTop10ByIndexingStatusOrderByCreatedAtAsc("processing").forEach(stale ->
            transaction.executeWithoutResult(status -> documents.findLockedById(stale.getId()).ifPresent(doc -> {
                if ("processing".equals(doc.getIndexingStatus()) && doc.getProcessingStartedAt() != null
                        && doc.getProcessingStartedAt().isBefore(Instant.now().minusSeconds(leaseSeconds))) {
                    doc.markFailed("PROCESSING_INTERRUPTED");
                }
            })));
        documents.findTop10ByIndexingStatusOrderByCreatedAtAsc("pending").forEach(doc -> {
            if (doc.getSourceKey() != null) process(doc.getId());
        });
    }

    public void process(Long id) {
        Job job = transaction.execute(status -> documents.findLockedById(id).map(doc -> {
            if (doc.getAttemptId() != null || !"pending".equals(doc.getIndexingStatus()) || doc.getSourceKey() == null) return null;
            String token = UUID.randomUUID().toString();
            doc.setIndexingStatus("processing");
            doc.setProcessingToken(token);
            doc.setProcessingStartedAt(Instant.now());
            return new Job(id, token, doc.getSourceKey(), doc.getSourceType());
        }).orElse(null));
        if (job == null) return;
        try {
            byte[] source = store.read(job.sourceKey());
            JsonNode result = processor.process(id, job.sourceType(), source);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
            if (!"ducky-document-v1".equals(result.path("schemaVersion").asText())
                    || !id.toString().equals(result.path("documentId").asText())
                    || !digest.equals(result.path("sourceSha256").asText())
                    || !result.path("chunks").isArray() || result.path("chunks").isEmpty()
                    || result.path("chunks").size() > 10000) {
                throw new IllegalStateException("INVALID_PROCESSING_RESULT");
            }
            transaction.executeWithoutResult(status -> documents.findLockedById(id).ifPresent(doc -> {
                if (!job.token().equals(doc.getProcessingToken()) || !"processing".equals(doc.getIndexingStatus())) return;
                try {
                    String prefix = job.sourceKey().substring(0, job.sourceKey().lastIndexOf('/') + 1);
                    store.write(prefix + "document.md", result.path("markdown").asText().getBytes(StandardCharsets.UTF_8));
                    StringBuilder jsonl = new StringBuilder();
                    result.path("canonicalBlocks").forEach(block -> jsonl.append(block).append('\n'));
                    store.write(prefix + "canonical.jsonl", jsonl.toString().getBytes(StandardCharsets.UTF_8));
                    store.write(prefix + "result.json", result.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception exception) { throw new IllegalStateException("ARTIFACT_WRITE_FAILED", exception); }
                chunks.deleteByDocument(doc);
                int index = 0;
                for (JsonNode node : result.path("chunks")) {
                    String content = node.path("content").asText();
                    if (content.isBlank()) throw new IllegalStateException("INVALID_PROCESSING_RESULT");
                    var chunk = DocumentChunk.create(doc, index++, content, embeddings.serialize(node.path("textForEmbedding").asText(content)));
                    chunk.setEmbeddingModel(SparseEmbeddingService.MODEL);
                    chunk.setBlockId(node.path("blockId").asText());
                    chunk.setHeadingPath(node.path("headingPath").asText());
                    chunk.setProvenanceJson(node.toString());
                    chunks.save(chunk);
                }
                doc.setChunkCount(index);
                doc.setProcessingWarnings(result.path("warnings").toString());
                doc.setSourceSha256(digest);
                doc.setProcessingSeconds(result.path("processingSeconds").asDouble());
                doc.markIndexed();
            }));
        } catch (Exception exception) {
            String message = exception.getMessage();
            String code = message != null && message.matches("[A-Z_]{1,60}") ? message : "PROCESSOR_UNAVAILABLE";
            transaction.executeWithoutResult(status -> documents.findLockedById(id).ifPresent(doc -> {
                if (job.token().equals(doc.getProcessingToken()) && "processing".equals(doc.getIndexingStatus())) doc.markFailed(code);
            }));
        }
    }
}
