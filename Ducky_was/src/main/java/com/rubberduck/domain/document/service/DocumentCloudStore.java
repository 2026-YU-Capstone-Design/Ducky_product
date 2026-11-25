package com.rubberduck.domain.document.service;

public interface DocumentCloudStore {
  record Uploaded(String versionId, String eTag) {}

  Uploaded upload(String key, byte[] data, String sha256);

  byte[] read(String key, String versionId, int maxBytes);
}
