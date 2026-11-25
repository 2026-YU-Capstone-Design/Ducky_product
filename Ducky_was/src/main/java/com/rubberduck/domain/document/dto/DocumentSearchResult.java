package com.rubberduck.domain.document.dto;

import java.util.List;

public record DocumentSearchResult(
        String documentId,
        String chunkId,
        String title,
        String content,
        double score,
        String blockId,
        String headingPath,
        Integer pageNumber,
        List<String> blockIds
) {
    public DocumentSearchResult {
        if (blockIds == null || blockIds.isEmpty()) {
            blockIds = blockId == null || blockId.isBlank() ? List.of() : List.of(blockId);
        } else {
            blockIds = List.copyOf(blockIds);
        }
    }
}
