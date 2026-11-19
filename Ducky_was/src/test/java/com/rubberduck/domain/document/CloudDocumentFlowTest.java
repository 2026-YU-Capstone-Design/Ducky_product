package com.rubberduck.domain.document;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rubberduck.domain.document.repository.*;
import com.rubberduck.domain.document.service.*;
import com.rubberduck.support.ApiFlowTest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(
    properties = {
      "document.mode=cloud",
      "document.cloud.bucket=test-documents",
      "document.cloud.completion-queue-url=http://localhost/queue",
      "document.cloud.processor-revision=release-test",
      "document.cloud.poll-enabled=false",
      "chat.response-mode=extractive"
    })
@ActiveProfiles("test")
class CloudDocumentFlowTest extends ApiFlowTest {
  @Autowired DocumentService service;
  @Autowired DocumentCompletionService completion;
  @Autowired LearningDocumentRepository documents;
  @Autowired DocumentUploadAttemptRepository attempts;
  @MockitoBean AwsDocumentAdapter store;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  DocumentIndexWriter indexWriter;

  final ObjectMapper mapper = new ObjectMapper();
  final byte[] source = "Quasar retry isolation policy".getBytes(StandardCharsets.UTF_8);
  Map<String, byte[]> objects;

  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry r) {
    String url = System.getenv("DOCUMENT_TEST_DB_URL");
    if (url != null) {
      r.add("spring.datasource.url", () -> url);
      r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
      r.add("spring.datasource.username", () -> "ducky_test");
      r.add("spring.datasource.password", () -> "local-test-only");
    }
  }

  @BeforeEach
  void setup() {
    objects = new HashMap<>();
    when(store.upload(anyString(), any(), anyString()))
        .thenAnswer(
            i -> {
              byte[] data = i.getArgument(1);
              assertThat(i.<String>getArgument(2)).isEqualTo(DocumentCompletionValidator.sha(data));
              objects.put(i.getArgument(0), data);
              return new DocumentCloudStore.Uploaded("version-1", "a".repeat(32));
            });
    when(store.read(anyString(), nullable(String.class), anyInt()))
        .thenAnswer(
            i -> {
              byte[] bytes = objects.get(i.<String>getArgument(0));
              if (bytes == null) throw new IllegalStateException("MISSING_TEST_OBJECT");
              return bytes;
            });
  }

  ObjectNode ref(String key, byte[] bytes) {
    objects.put(key, bytes);
    return mapper.valueToTree(
        Map.of(
            "key", key, "bytes", bytes.length, "sha256", DocumentCompletionValidator.sha(bytes)));
  }

  record Fixture(Long id, ObjectNode message, ObjectNode manifest, String prefix) {}

  Fixture fixture(com.rubberduck.domain.user.entity.User user) {
    Long id =
        Long.valueOf(
            service
                .upload(user, new MockMultipartFile("file", "demo.txt", "text/plain", source))
                .id());
    var d = documents.findById(id).orElseThrow();
    ObjectNode src =
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
    job.put("processorRevision", "release-test");
    job.put("resultSchemaVersion", "ducky-document-v1");
    job.put("normalizerVersion", "ducky-learning-v2");
    job.put("chunkerVersion", "ducky-page-chunks-v2");
    String jobId = DocumentCompletionValidator.sha(mapper.writeValueAsBytes(job));
    String prefix =
        "markdown/" + user.getId() + "/" + id + "/" + d.getAttemptId() + "/" + jobId + "/";
    String run = prefix + "runs/" + UUID.randomUUID() + "/";
    var block =
        Map.of(
            "doc_id",
            id.toString(),
            "block_id",
            "block-1",
            "source",
            Map.of("markdown_key", run + "document.md"));
    Map<String, Object> result = new HashMap<>();
    result.put("schemaVersion", "ducky-document-v1");
    result.put("documentId", id.toString());
    result.put("sourceSha256", d.getSourceSha256());
    result.put("markdown", new String(source, StandardCharsets.UTF_8));
    result.put("canonicalBlocks", List.of(block));
    result.put(
        "chunks",
        List.of(
            Map.of("content", new String(source, StandardCharsets.UTF_8), "blockId", "block-1")));
    result.put("warnings", List.of());
    result.put("pageCount", null);
    result.put("report", Map.of("source_key", run + "document.md"));
    result.put("processingSeconds", 0.1);
    String precursor = System.getenv("DOCUMENT_PRECURSOR_RESULT");
    if (precursor != null) {
      try {
        String raw =
            java.nio.file.Files.readAllLines(java.nio.file.Path.of(precursor)).stream()
                .filter(line -> line.startsWith("{"))
                .reduce((a, b) -> b)
                .orElseThrow();
        raw =
            raw.replace("987654321", id.toString())
                .replace("MARKDOWN_PLACEHOLDER", run + "document.md")
                .replace("SOURCE_PLACEHOLDER", d.getSourceKey());
        result = mapper.readValue(raw, Map.class);
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
    }
    ObjectNode artifacts = mapper.createObjectNode();
    artifacts.set(
        "markdown",
        ref(
            run + "document.md",
            result.get("markdown").toString().getBytes(StandardCharsets.UTF_8)));
    StringBuilder canonical = new StringBuilder();
    for (Object canonicalBlock : (List<?>) result.get("canonicalBlocks"))
      canonical.append(mapper.writeValueAsString(canonicalBlock)).append('\n');
    artifacts.set(
        "canonical",
        ref(run + "canonical.jsonl", canonical.toString().getBytes(StandardCharsets.UTF_8)));
    artifacts.set("result", ref(run + "result.json", mapper.writeValueAsBytes(result)));
    ObjectNode c = mapper.createObjectNode();
    c.put("schemaVersion", "ducky-document-completion-v1");
    c.put("jobId", jobId);
    c.put("userId", user.getId().toString());
    c.put("documentId", id.toString());
    c.put("attemptId", d.getAttemptId());
    c.put("processorRevision", "release-test");
    c.put("status", "processed");
    c.putNull("errorCode");
    c.set("source", src);
    ObjectNode m = c.deepCopy();
    m.put("schemaVersion", "ducky-document-manifest-v1");
    m.put("sourceSequencer", "ABC123");
    m.put("resultSchemaVersion", "ducky-document-v1");
    m.put("normalizerVersion", "ducky-learning-v2");
    m.put("chunkerVersion", "ducky-page-chunks-v2");
    m.put("sourceSha256", d.getSourceSha256());
    m.put("chunkCount", 1);
    m.putNull("pageCount");
    m.set("processingSeconds", mapper.valueToTree(result.get("processingSeconds")));
    m.set("warnings", mapper.createArrayNode());
    m.set("artifacts", artifacts);
    var f = new Fixture(id, c, m, prefix);
    refresh(f);
    return f;
  }

  void refresh(Fixture f) {
    var r = ref(f.prefix() + "manifest.json", mapper.writeValueAsBytes(f.manifest()));
    r.put("bucket", "test-documents");
    f.message().set("manifest", r);
  }

  @Test
  void indexesOnlyAfterValidationAndIsIdempotentAndOwnerScoped() {
    var owner = newUser("cloud");
    var other = newUser("other");
    var f = fixture(owner);
    assertThat(attempts.findById(f.message().path("attemptId").asText())).isPresent();
    completion.complete(f.message().toString());
    completion.complete(f.message().toString());
    assertThat(service.get(owner, f.id()).indexingStatus()).isEqualTo("indexed");
    assertThat(service.searchResults(owner, "Quasar retry", 5)).hasSize(1);
    assertThat(service.searchResults(other, "Quasar retry", 5)).isEmpty();
    assertThatThrownBy(() -> service.retry(other, f.id())).isInstanceOf(RuntimeException.class);
    service.delete(owner, f.id());
    completion.complete(f.message().toString());
    assertThat(documents.findById(f.id())).isEmpty();
    var replacement = fixture(owner);
    assertThat(replacement.id()).isNotEqualTo(f.id());
    completion.complete(f.message().toString());
    assertThat(service.get(owner, replacement.id()).indexingStatus()).isEqualTo("pending");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "manifestHash",
        "artifactHash",
        "artifactPrefix",
        "owner",
        "attempt",
        "revision",
        "source",
        "manifestOwner",
        "resultHash",
        "resultDoc",
        "chunkLimit",
        "schema",
        "status"
      })
  void rejectsTampering(String mutation) {
    var owner = newUser("tamper");
    var f = fixture(owner);
    var c = f.message();
    var m = f.manifest();
    switch (mutation) {
      case "manifestHash" -> {
        refresh(f);
        ((ObjectNode) c.path("manifest")).put("sha256", "0".repeat(64));
      }
      case "artifactHash" ->
          ((ObjectNode) m.path("artifacts").path("markdown")).put("sha256", "0".repeat(64));
      case "artifactPrefix" ->
          ((ObjectNode) m.path("artifacts").path("markdown"))
              .put("key", "markdown/other/document.md");
      case "owner" -> c.put("userId", "999999");
      case "attempt" -> m.put("attemptId", UUID.randomUUID().toString());
      case "revision" -> c.put("processorRevision", "wrong-release");
      case "source" -> ((ObjectNode) c.path("source")).put("versionId", "wrong-version");
      case "manifestOwner" -> m.put("userId", "999999");
      case "resultHash", "resultDoc", "chunkLimit" -> {
        var a = (ObjectNode) m.path("artifacts");
        String key = a.path("result").path("key").asText();
        var result = (ObjectNode) mapper.readTree(objects.get(key));
        if (mutation.equals("resultHash")) result.put("sourceSha256", "0".repeat(64));
        if (mutation.equals("resultDoc")) result.put("documentId", "999999");
        if (mutation.equals("chunkLimit")) {
          var arr = mapper.createArrayNode();
          for (int i = 0; i < 10001; i++) arr.add(mapper.valueToTree(Map.of("content", "x")));
          result.set("chunks", arr);
        }
        a.set("result", ref(key, mapper.writeValueAsBytes(result)));
      }
      case "schema" -> c.put("schemaVersion", "wrong");
      case "status" -> m.put("status", "indexed");
    }
    if (!mutation.equals("manifestHash")) refresh(f);
    assertThatThrownBy(() -> completion.complete(c.toString()))
        .isInstanceOf(RuntimeException.class);
    assertThat(service.get(owner, f.id()).indexingStatus()).isEqualTo("pending");
    assertThat(service.searchResults(owner, "Quasar", 5)).isEmpty();
  }

  @Test
  void latePriorAttemptCannotOverrideRetry() {
    var owner = newUser("retry");
    var old = fixture(owner);
    old.message().put("status", "failed");
    old.message().put("errorCode", "OCR_REQUIRED");
    old.manifest().put("status", "failed");
    old.manifest().put("errorCode", "OCR_REQUIRED");
    old.manifest().set("artifacts", mapper.createObjectNode());
    refresh(old);
    completion.complete(old.message().toString());
    assertThat(service.get(owner, old.id()).indexingStatus()).isEqualTo("failed");
    var retried = service.retry(owner, old.id());
    assertThat(retried.attemptId()).isNotEqualTo(old.message().path("attemptId").asText());
    assertThat(attempts.findById(retried.attemptId())).isPresent();
    completion.complete(old.message().toString());
    assertThat(service.get(owner, old.id()).indexingStatus()).isEqualTo("pending");
  }

  @Test
  void databaseIndexFailureRollsBackAndRedeliveryCanSucceed() {
    var owner = newUser("rollback");
    var f = fixture(owner);
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new org.springframework.dao.DataAccessResourceFailureException(
                  "SIMULATED_DB_FAILURE");
            })
        .when(indexWriter)
        .replace(any(), any());
    assertThatThrownBy(() -> completion.complete(f.message().toString()))
        .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    assertThat(service.get(owner, f.id()).indexingStatus()).isEqualTo("pending");
    assertThat(service.searchResults(owner, "Quasar", 5)).isEmpty();
    assertThat(documents.findById(f.id()).orElseThrow().getCompletedJobId()).isNull();
    doCallRealMethod().when(indexWriter).replace(any(), any());
    completion.complete(f.message().toString());
    assertThat(service.get(owner, f.id()).indexingStatus()).isEqualTo("indexed");
  }

  @Test
  void concurrentDuplicatesSerializeOnDocumentLock() {
    var owner = newUser("concurrent");
    var f = fixture(owner);
    java.util.concurrent.CompletableFuture.allOf(
            java.util.concurrent.CompletableFuture.runAsync(
                () -> completion.complete(f.message().toString())),
            java.util.concurrent.CompletableFuture.runAsync(
                () -> completion.complete(f.message().toString())))
        .join();
    assertThat(service.get(owner, f.id()).chunkCount()).isEqualTo(1);
    assertThat(service.searchResults(owner, "Quasar", 5)).hasSize(1);
  }

  @Test
  void metadataRaceDoesNotAcknowledge() {
    var owner = newUser("metadata");
    var f = fixture(owner);
    var d = documents.findById(f.id()).orElseThrow();
    d.setSourceVersionId(null);
    documents.saveAndFlush(d);
    assertThatThrownBy(() -> completion.complete(f.message().toString()))
        .hasMessage("UPLOAD_METADATA_PENDING");
    d.setSourceVersionId("version-1");
    documents.saveAndFlush(d);
    completion.complete(f.message().toString());
    assertThat(service.get(owner, f.id()).indexingStatus()).isEqualTo("indexed");
  }
}
