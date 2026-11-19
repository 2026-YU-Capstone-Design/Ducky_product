package com.rubberduck.domain.document.service;

import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentCompletionService {
  private final LearningDocumentRepository documents;
  private final DocumentCompletionValidator validator;
  private final ObjectProvider<DocumentCloudStore> stores;
  private final DocumentIndexWriter index;
  private final TransactionTemplate tx;

  public DocumentCompletionService(
      LearningDocumentRepository d,
      DocumentCompletionValidator v,
      ObjectProvider<DocumentCloudStore> s,
      DocumentIndexWriter i,
      PlatformTransactionManager m) {
    documents = d;
    validator = v;
    stores = s;
    index = i;
    tx = new TransactionTemplate(m);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Returns only after commit; the queue adapter may then acknowledge. Exceptions must remain
   * unacked.
   */
  public void complete(String body) {
    var c = validator.parse(body);
    validator.envelope(c);
    tx.executeWithoutResult(
        status -> {
          var found = documents.findLockedById(Long.valueOf(c.path("documentId").asText()));
          if (found.isEmpty()) return;
          var d = found.get();
          DocumentCompletionValidator.require(
              d.getUser().getId().toString().equals(c.path("userId").asText()));
          if (!c.path("attemptId").asText().equals(d.getAttemptId())) return;
          if (d.getSourceVersionId() == null || d.getSourceETag() == null)
            throw new IllegalStateException("UPLOAD_METADATA_PENDING");
          var result = validator.validate(c, d, stores.getObject());
          if (d.getCompletedJobId() != null) {
            DocumentCompletionValidator.require(
                d.getCompletedJobId().equals(c.path("jobId").asText()));
            return;
          }
          if ("failed".equals(c.path("status").asText()))
            d.markFailed(c.path("errorCode").asText());
          else {
            d.setIndexingStatus("processed");
            index.replace(d, result);
            d.markIndexed();
          }
          d.setCompletedJobId(c.path("jobId").asText());
        });
  }
}
