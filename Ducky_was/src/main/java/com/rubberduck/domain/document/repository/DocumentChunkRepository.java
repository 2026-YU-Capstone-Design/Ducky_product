package com.rubberduck.domain.document.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rubberduck.domain.document.entity.DocumentChunk;
import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.user.entity.User;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocument(LearningDocument document);

    long countByDocument(LearningDocument document);

    List<DocumentChunk> findByDocumentOrderByChunkIndexAscIdAsc(LearningDocument document, Pageable pageable);

    @Query("""
            select chunk from DocumentChunk chunk join fetch chunk.document document
            where document.user = :user and document.ragEnabled = true and document.indexingStatus = 'indexed'
            """)
    List<DocumentChunk> findSearchableByUser(@Param("user") User user);

    @Query("""
            select chunk from DocumentChunk chunk join fetch chunk.document document
            where document.user = :user and document.ragEnabled = true and document.indexingStatus = 'indexed'
              and document.id in :documentIds
            """)
    List<DocumentChunk> findSearchableByUserAndDocuments(
            @Param("user") User user, @Param("documentIds") List<Long> documentIds);

    void deleteByDocument(LearningDocument document);
}
