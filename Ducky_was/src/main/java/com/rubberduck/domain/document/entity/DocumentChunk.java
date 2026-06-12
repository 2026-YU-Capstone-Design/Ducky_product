package com.rubberduck.domain.document.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private LearningDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Lob
    @Column(nullable = false)
    private String content;

    @Lob
    @Column(name = "embedding_json", nullable = false)
    private String embeddingJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DocumentChunk create(
            LearningDocument document,
            int chunkIndex,
            String content,
            String embeddingJson
    ) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setEmbeddingJson(embeddingJson);
        return chunk;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
