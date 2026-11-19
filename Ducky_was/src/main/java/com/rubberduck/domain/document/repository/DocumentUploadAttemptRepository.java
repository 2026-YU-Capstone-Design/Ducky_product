package com.rubberduck.domain.document.repository;

import com.rubberduck.domain.document.entity.DocumentUploadAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentUploadAttemptRepository
    extends JpaRepository<DocumentUploadAttempt, String> {}
