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
        String lastUsedAt
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
                null
        );
    }
}
