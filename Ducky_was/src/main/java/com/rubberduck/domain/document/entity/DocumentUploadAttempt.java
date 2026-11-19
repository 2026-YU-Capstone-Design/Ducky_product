package com.rubberduck.domain.document.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** Attempt history deliberately survives document deletion; contains no document bytes. */
@Entity
@Table(name = "document_upload_attempts")
@Getter
@Setter
@NoArgsConstructor
public class DocumentUploadAttempt {
  @Id
  @Column(length = 36)
  private String attemptId;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Long documentId;

  @Column(nullable = false, length = 255)
  private String sourceKey;

  @Column(nullable = false, length = 255)
  private String sourceBucket;

  @Column(nullable = false, length = 64)
  private String sourceSha256;

  @Column(length = 1024)
  private String sourceVersionId;

  @Column(name = "source_etag", length = 128)
  private String sourceETag;

  @Column(nullable = false)
  private long sourceSize;

  @Column(nullable = false)
  private Instant createdAt;

  public static DocumentUploadAttempt from(LearningDocument d) {
    var a = new DocumentUploadAttempt();
    a.attemptId = d.getAttemptId();
    a.userId = d.getUser().getId();
    a.documentId = d.getId();
    a.sourceKey = d.getSourceKey();
    a.sourceBucket = d.getSourceBucket();
    a.sourceSha256 = d.getSourceSha256();
    a.sourceSize = d.getFileSize();
    a.createdAt = Instant.now();
    return a;
  }
}
