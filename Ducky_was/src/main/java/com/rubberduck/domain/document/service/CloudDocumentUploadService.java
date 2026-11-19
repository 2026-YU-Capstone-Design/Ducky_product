package com.rubberduck.domain.document.service;

import com.rubberduck.domain.document.dto.DocumentResponse;
import com.rubberduck.domain.document.entity.LearningDocument;
import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudDocumentUploadService {
  private final LearningDocumentRepository documents;
  private final ObjectProvider<DocumentCloudStore> stores;
  private final CloudDocumentSettings settings;
  private final TransactionTemplate tx;
  private final com.rubberduck.domain.document.repository.DocumentUploadAttemptRepository attempts;

  record Upload(Long id, String attempt, String key, String digest, byte[] data) {}

  public CloudDocumentUploadService(
      LearningDocumentRepository d,
      ObjectProvider<DocumentCloudStore> s,
      CloudDocumentSettings c,
      PlatformTransactionManager m,
      com.rubberduck.domain.document.repository.DocumentUploadAttemptRepository attempts) {
    this.attempts = attempts;
    documents = d;
    stores = s;
    settings = c;
    tx = new TransactionTemplate(m);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public DocumentResponse upload(User user, MultipartFile file, String name) {
    String type = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (!List.of("pdf", "txt", "md", "csv", "json").contains(type))
      throw new CustomException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
    byte[] data;
    try {
      data = file.getBytes();
    } catch (java.io.IOException e) {
      throw new CustomException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE);
    }
    if (data.length == 0 || data.length > 20 * 1024 * 1024)
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    return send(
        tx.execute(
            s -> {
              var d =
                  documents.save(
                      LearningDocument.create(
                          user,
                          name.substring(0, Math.min(160, name.length())),
                          name,
                          file.getContentType(),
                          data.length,
                          type));
              d.setSourceType(type);
              return prepare(d, data);
            }));
  }

  private Upload prepare(LearningDocument d, byte[] data) {
    String a = UUID.randomUUID().toString(),
        key =
            "originals/"
                + d.getUser().getId()
                + "/"
                + d.getId()
                + "/"
                + a
                + "/source."
                + d.getSourceType();
    d.setAttemptId(a);
    d.setSourceKey(key);
    d.setSourceBucket(settings.bucket());
    d.setSourceSha256(DocumentCompletionValidator.sha(data));
    d.setSourceVersionId(null);
    d.setSourceETag(null);
    d.setCompletedJobId(null);
    d.setProcessorRevision(settings.revision());
    d.setIndexingStatus("pending");
    d.setStatus("queued");
    d.setProcessingError(null);
    attempts.save(com.rubberduck.domain.document.entity.DocumentUploadAttempt.from(d));
    return new Upload(d.getId(), a, key, d.getSourceSha256(), data);
  }

  public DocumentResponse retry(User user, Long id) {
    return send(
        tx.execute(
            s -> {
              var d =
                  documents
                      .findLockedById(id)
                      .filter(x -> x.getUser().getId().equals(user.getId()))
                      .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
              if (!"failed".equals(d.getIndexingStatus())
                  || d.getAttemptId() == null
                  || d.getSourceVersionId() == null)
                throw new CustomException(ErrorCode.DOCUMENT_RETRY_NOT_ALLOWED);
              byte[] data =
                  stores
                      .getObject()
                      .read(d.getSourceKey(), d.getSourceVersionId(), 20 * 1024 * 1024);
              if (data.length != d.getFileSize()
                  || !DocumentCompletionValidator.sha(data).equals(d.getSourceSha256()))
                throw new IllegalStateException("SOURCE_HASH_MISMATCH");
              return prepare(d, data);
            }));
  }

  private DocumentResponse send(Upload u) {
    DocumentCloudStore.Uploaded saved;
    try {
      saved = stores.getObject().upload(u.key(), u.data(), u.digest());
      if (saved.versionId() == null
          || saved.versionId().isBlank()
          || "null".equals(saved.versionId())
          || saved.eTag() == null
          || !saved.eTag().matches("[0-9a-f]{32}(?:-[1-9][0-9]*)?"))
        throw new IllegalStateException("VERSIONED_UPLOAD_REQUIRED");
    } catch (RuntimeException e) {
      tx.executeWithoutResult(
          s ->
              documents
                  .findLockedById(u.id())
                  .ifPresent(
                      d -> {
                        if (u.attempt().equals(d.getAttemptId()))
                          d.markFailed("SOURCE_UPLOAD_FAILED");
                      }));
      throw new CustomException(ErrorCode.DOCUMENT_STORAGE_UNAVAILABLE);
    }
    return tx.execute(
        s -> {
          var d = documents.findLockedById(u.id()).orElseThrow();
          if (!u.attempt().equals(d.getAttemptId()))
            throw new IllegalStateException("ATTEMPT_CHANGED");
          d.setSourceVersionId(saved.versionId());
          d.setSourceETag(saved.eTag());
          var a = attempts.findById(u.attempt()).orElseThrow();
          a.setSourceVersionId(saved.versionId());
          a.setSourceETag(saved.eTag());
          return DocumentResponse.from(d);
        });
  }
}
