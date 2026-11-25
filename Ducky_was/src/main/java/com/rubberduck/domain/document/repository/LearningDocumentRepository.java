package com.rubberduck.domain.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.user.entity.User;

public interface LearningDocumentRepository extends JpaRepository<LearningDocument, Long> {

    List<LearningDocument> findByUserOrderByCreatedAtDesc(User user);

    Optional<LearningDocument> findByIdAndUser(Long id, User user);

    List<LearningDocument> findTop10ByIndexingStatusOrderByCreatedAtAsc(String indexingStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from LearningDocument d where d.id = :id")
    Optional<LearningDocument> findLockedById(Long id);
}
