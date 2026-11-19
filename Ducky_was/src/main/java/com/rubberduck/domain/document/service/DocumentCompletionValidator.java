package com.rubberduck.domain.document.service;

import com.rubberduck.domain.document.entity.LearningDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Treat the queue and every object as untrusted; no caller supplied ownership. */
@Component
public class DocumentCompletionValidator {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CloudDocumentSettings settings;

  public DocumentCompletionValidator(CloudDocumentSettings settings) {
    this.settings = settings;
  }

  public static String sha(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static void require(boolean ok) {
    if (!ok) throw new IllegalArgumentException("INVALID_DOCUMENT_COMPLETION");
  }

  public JsonNode parse(String body) {
    require(body.length() <= 262144);
    return mapper.readTree(body);
  }

  public void envelope(JsonNode c) {
    require("ducky-document-completion-v1".equals(c.path("schemaVersion").asText()));
    for (String key : List.of("userId", "documentId"))
      require(c.path(key).isString() && c.path(key).asText().matches("[1-9][0-9]{0,18}"));
    require(c.path("jobId").asText().matches("[0-9a-f]{64}"));
    String attempt = c.path("attemptId").asText();
    require(UUID.fromString(attempt).toString().equals(attempt));
    require(List.of("processed", "failed").contains(c.path("status").asText()));
  }

  public JsonNode validate(JsonNode c, LearningDocument d, DocumentCloudStore store) {
    require(d.getUser().getId().toString().equals(c.path("userId").asText()));
    require(d.getProcessorRevision().equals(c.path("processorRevision").asText()));
    var source =
        mapper.valueToTree(
            Map.of(
                "bucket",
                d.getSourceBucket(),
                "key",
                d.getSourceKey(),
                "versionId",
                d.getSourceVersionId(),
                "eTag",
                d.getSourceETag(),
                "size",
                d.getFileSize()));
    for (String key : List.of("bucket", "key", "versionId", "eTag"))
      require(source.path(key).equals(c.path("source").path(key)));
    require(
        c.path("source").size() == 5
            && c.path("source").path("size").isIntegralNumber()
            && c.path("source").path("size").asText().equals(Long.toString(d.getFileSize())));
    String expectedSource =
        "originals/"
            + d.getUser().getId()
            + "/"
            + d.getId()
            + "/"
            + d.getAttemptId()
            + "/source."
            + d.getSourceType();
    require(
        expectedSource.equals(d.getSourceKey()) && settings.bucket().equals(d.getSourceBucket()));
    require(!"null".equals(d.getSourceVersionId()));
    Map<String, Object> job = new TreeMap<>();
    job.put(
        "source",
        new TreeMap<>(
            Map.of(
                "bucket",
                d.getSourceBucket(),
                "key",
                d.getSourceKey(),
                "versionId",
                d.getSourceVersionId(),
                "eTag",
                d.getSourceETag(),
                "size",
                d.getFileSize())));
    job.put("processorRevision", d.getProcessorRevision());
    job.put("resultSchemaVersion", "ducky-document-v1");
    job.put("normalizerVersion", settings.normalizer());
    job.put("chunkerVersion", settings.chunker());
    require(sha(mapper.writeValueAsBytes(job)).equals(c.path("jobId").asText()));
    String prefix =
        "markdown/"
            + d.getUser().getId()
            + "/"
            + d.getId()
            + "/"
            + d.getAttemptId()
            + "/"
            + c.path("jobId").asText()
            + "/";
    var ref = c.path("manifest");
    require(settings.bucket().equals(ref.path("bucket").asText()));
    byte[] raw = artifact(store, ref, prefix + "manifest.json", 1024 * 1024);
    JsonNode m = mapper.readTree(raw);
    require("ducky-document-manifest-v1".equals(m.path("schemaVersion").asText()));
    for (String key :
        List.of(
            "jobId",
            "userId",
            "documentId",
            "attemptId",
            "processorRevision",
            "status",
            "errorCode",
            "source")) require(m.path(key).equals(c.path(key)));
    require("ducky-document-v1".equals(m.path("resultSchemaVersion").asText()));
    require(
        settings.normalizer().equals(m.path("normalizerVersion").asText())
            && settings.chunker().equals(m.path("chunkerVersion").asText()));
    require(m.path("sourceSequencer").asText().matches("[0-9A-Fa-f]+"));
    if ("failed".equals(m.path("status").asText())) {
      require(
          m.path("errorCode").asText().matches("[A-Z_]{1,60}")
              && m.path("artifacts").isObject()
              && m.path("artifacts").isEmpty());
      return m;
    }
    require(
        m.path("errorCode").isNull()
            && d.getSourceSha256().equals(m.path("sourceSha256").asText()));
    var a = m.path("artifacts");
    require(a.isObject() && a.size() == 3);
    String resultKey = a.path("result").path("key").asText();
    require(resultKey.startsWith(prefix + "runs/"));
    String run = resultKey.substring((prefix + "runs/").length());
    require(run.matches("[0-9a-f-]{36}/result[.]json"));
    String runId = run.substring(0, 36);
    require(UUID.fromString(runId).toString().equals(runId));
    String runPrefix = prefix + "runs/" + runId + "/";
    byte[] markdown =
        artifact(store, a.path("markdown"), runPrefix + "document.md", 32 * 1024 * 1024);
    byte[] canonical =
        artifact(store, a.path("canonical"), runPrefix + "canonical.jsonl", 64 * 1024 * 1024);
    JsonNode result =
        mapper.readTree(
            artifact(store, a.path("result"), runPrefix + "result.json", 64 * 1024 * 1024));
    require(
        "ducky-document-v1".equals(result.path("schemaVersion").asText())
            && d.getId().toString().equals(result.path("documentId").asText()));
    require(d.getSourceSha256().equals(result.path("sourceSha256").asText()));
    var chunks = result.path("chunks");
    require(chunks.isArray() && !chunks.isEmpty() && chunks.size() <= 10000);
    require(
        m.path("chunkCount").isIntegralNumber()
            && m.path("chunkCount").asText().equals(Integer.toString(chunks.size())));
    require(
        "pdf".equals(d.getSourceType())
            ? m.path("pageCount").isIntegralNumber()
                && m.path("pageCount").asLong() >= 1
                && m.path("pageCount").asLong() <= 200
                && m.path("pageCount").asText().equals(Long.toString(m.path("pageCount").asLong()))
            : m.path("pageCount").isNull());
    require(m.path("pageCount").equals(result.path("pageCount")));
    require(
        result.path("warnings").isArray() && result.path("warnings").equals(m.path("warnings")));
    require(result.path("processingSeconds").equals(m.path("processingSeconds")));
    require(
        Arrays.equals(markdown, result.path("markdown").asText().getBytes(StandardCharsets.UTF_8)));
    require(
        runPrefix.concat("document.md").equals(result.path("report").path("source_key").asText()));
    require(result.path("canonicalBlocks").isArray());
    var lines = new String(canonical, StandardCharsets.UTF_8).lines().toList();
    require(lines.size() == result.path("canonicalBlocks").size());
    for (int i = 0; i < lines.size(); i++) {
      var block = mapper.readTree(lines.get(i));
      require(block.equals(result.path("canonicalBlocks").get(i)));
      require(d.getId().toString().equals(block.path("doc_id").asText()));
      require(
          runPrefix
              .concat("document.md")
              .equals(block.path("source").path("markdown_key").asText()));
    }
    return result;
  }

  private byte[] artifact(DocumentCloudStore store, JsonNode ref, String expected, int cap) {
    require(
        expected.equals(ref.path("key").asText())
            && ref.path("sha256").asText().matches("[0-9a-f]{64}"));
    require(
        ref.path("bytes").isIntegralNumber()
            && ref.path("bytes").asLong() >= 0
            && ref.path("bytes").asLong() <= cap);
    byte[] bytes = store.read(expected, null, cap);
    require(
        ref.path("bytes").asText().equals(Integer.toString(bytes.length))
            && sha(bytes).equals(ref.path("sha256").asText()));
    return bytes;
  }
}
