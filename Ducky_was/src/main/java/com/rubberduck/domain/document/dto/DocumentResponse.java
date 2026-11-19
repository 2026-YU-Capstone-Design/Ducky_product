package com.rubberduck.domain.document.dto;

import com.rubberduck.domain.document.entity.LearningDocument;

public record DocumentResponse(
        String id,
        String title,
        String fileName,
        long fileSize,
        String kind,
        String status,
        String source,
        boolean ragEnabled,
        String indexingStatus,
        String uploadedAt,
        String lastUsedAt,
        String processingError,
        String processingWarnings,
        Integer chunkCount,
        Double processingSeconds,
        String attemptId,
        String sourceType,
        String sourceSha256,
        String sourceVersionId,
        String sourceETag,
        boolean retryAllowed
) {

    public static DocumentResponse from(LearningDocument document) {
        return new DocumentResponse(
                String.valueOf(document.getId()),
                document.getTitle(),
                document.getFileName(),
                document.getFileSize(),
                document.getKind(),
                document.getStatus(),
                document.getSource(),
                document.isRagEnabled(),
                document.getIndexingStatus(),
                document.getCreatedAt().toString(),
                null,
                document.getProcessingError(),
                document.getProcessingWarnings(),
                document.getChunkCount(),
                document.getProcessingSeconds(),
                document.getAttemptId(),
                document.getSourceType(),
                document.getSourceSha256(),
                document.getSourceVersionId(),
                document.getSourceETag(),
                "failed".equals(document.getIndexingStatus()) && document.getSourceKey() != null
                        && (document.getAttemptId() == null || document.getSourceVersionId() != null)
        );
    }
}
