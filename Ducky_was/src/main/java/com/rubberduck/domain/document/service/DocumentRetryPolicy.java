package com.rubberduck.domain.document.service;

import com.rubberduck.domain.document.entity.LearningDocument;

/** The UI capability and retry command share the same execution-mode decision. */
public final class DocumentRetryPolicy {
  private DocumentRetryPolicy() {}

  public static boolean allowed(LearningDocument doc, boolean cloud, boolean pipelineEnabled) {
    if (!"failed".equals(doc.getIndexingStatus())
        || doc.getSourceKey() == null
        || doc.getSourceKey().isBlank()) return false;
    return cloud
        ? doc.getAttemptId() != null
            && doc.getSourceVersionId() != null
            && !doc.getSourceVersionId().isBlank()
            && !"null".equals(doc.getSourceVersionId())
        : pipelineEnabled && doc.getAttemptId() == null;
  }
}
