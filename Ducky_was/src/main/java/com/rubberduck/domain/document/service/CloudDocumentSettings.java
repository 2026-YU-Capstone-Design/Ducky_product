package com.rubberduck.domain.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record CloudDocumentSettings(
    @Value("${document.mode:local}") String mode,
    @Value("${document.cloud.bucket:}") String bucket,
    @Value("${document.cloud.region:ap-southeast-2}") String region,
    @Value("${document.cloud.completion-queue-url:}") String queueUrl,
    @Value("${document.cloud.processor-revision:}") String revision,
    @Value("${document.cloud.normalizer-version:ducky-learning-v2}") String normalizer,
    @Value("${document.cloud.chunker-version:ducky-page-chunks-v2}") String chunker) {
  public CloudDocumentSettings {
    if (!mode.equals("local") && !mode.equals("cloud"))
      throw new IllegalArgumentException("INVALID_DOCUMENT_MODE");
    if (mode.equals("cloud") && (bucket.isBlank() || queueUrl.isBlank() || revision.isBlank()))
      throw new IllegalArgumentException("DOCUMENT_CLOUD_SETTINGS_REQUIRED");
  }

  public boolean enabled() {
    return mode.equals("cloud");
  }
}
