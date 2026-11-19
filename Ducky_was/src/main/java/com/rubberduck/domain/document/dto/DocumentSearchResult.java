package com.rubberduck.domain.document.dto;

public record DocumentSearchResult(
        String documentId,
        String chunkId,
        String title,
        String content,
        double score,
        String blockId,
        String headingPath,
        java.util.List<String> blockIds,
        Integer pageNumber,
        java.util.List<String> contextChunkIds
) {
    public DocumentSearchResult {
        blockIds = blockIds == null ? (blockId == null ? java.util.List.of() : java.util.List.of(blockId)) : java.util.List.copyOf(blockIds);
        contextChunkIds = contextChunkIds == null ? (chunkId == null ? java.util.List.of() : java.util.List.of(chunkId)) : java.util.List.copyOf(contextChunkIds);
    }

    public DocumentSearchResult(String documentId, String chunkId, String title, String content,
            double score, String blockId, String headingPath) {
        this(documentId, chunkId, title, content, score, blockId, headingPath, null, null, null);
    }
}
