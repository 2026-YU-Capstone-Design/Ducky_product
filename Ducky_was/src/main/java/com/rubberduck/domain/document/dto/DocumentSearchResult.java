package com.rubberduck.domain.document.dto;

public record DocumentSearchResult(
        String documentId,
        String chunkId,
        String title,
        String content,
        double score
) {
}
