package com.rubberduck.domain.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.user.entity.User;

public interface LearningDocumentRepository extends JpaRepository<LearningDocument, Long> {

    List<LearningDocument> findByUserOrderByCreatedAtDesc(User user);

    Optional<LearningDocument> findByIdAndUser(Long id, User user);
}
