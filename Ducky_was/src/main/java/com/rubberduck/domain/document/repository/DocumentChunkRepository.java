package com.rubberduck.domain.document.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.document.entity.DocumentChunk;
import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.user.entity.User;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocument(LearningDocument document);

    long countByDocument(LearningDocument document);

    List<DocumentChunk> findByDocumentOrderByChunkIndexAscIdAsc(LearningDocument document, Pageable pageable);

    List<DocumentChunk> findByDocument_UserAndDocument_RagEnabledTrue(User user);

    void deleteByDocument(LearningDocument document);
}
