package com.rubberduck.domain.document.service;

import java.util.*;
import tools.jackson.databind.JsonNode;

/**
 * Shared result semantics for HTTP results and cloud artifacts; legacy optional fields stay
 * optional.
 */
public final class DocumentResultValidator {
  private DocumentResultValidator() {}

  static void require(boolean valid) {
    if (!valid) throw new IllegalArgumentException("INVALID_PROCESSING_RESULT");
  }

  static String text(JsonNode node, int max) {
    require(node.isString());
    String value = node.asText();
    require(!value.isBlank() && value.length() <= max);
    return value;
  }

  static Integer page(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) return null;
    require(
        node.isIntegralNumber()
            && node.asLong() >= 1
            && node.asLong() <= 200
            && node.asText().equals(Long.toString(node.asLong())));
    return node.asInt();
  }

  public static void validate(JsonNode result) {
    require(result.isObject());
    require("ducky-document-v1".equals(text(result.path("schemaVersion"), 80)));
    String documentId = text(result.path("documentId"), 19);
    require(documentId.matches("[1-9][0-9]{0,18}"));
    require(text(result.path("sourceSha256"), 64).matches("[0-9a-f]{64}"));
    require(result.path("markdown").isString());
    require(result.path("warnings").isArray());
    result.path("warnings").forEach(w -> require(w.isString()));
    var duration = result.path("processingSeconds");
    require(
        duration.isNumber() && Double.isFinite(duration.asDouble()) && duration.asDouble() >= 0);
    Integer pageCount = page(result.path("pageCount"));
    var canonical = result.path("canonicalBlocks");
    require(canonical.isArray() && !canonical.isEmpty());
    Map<String, JsonNode> blocks = new HashMap<>();
    for (JsonNode block : canonical) {
      require(block.isObject());
      String id = text(block.path("block_id"), 100);
      require(
          documentId.equals(text(block.path("doc_id"), 19))
              && blocks.putIfAbsent(id, block) == null);
      var source = block.path("source");
      // Older HTTP fixtures/contracts may omit source/page entirely.
      if (!source.isMissingNode()) require(source.isObject());
      Integer blockPage = page(source.path("page"));
      if (blockPage != null && pageCount != null) require(blockPage <= pageCount);
    }
    var chunks = result.path("chunks");
    require(chunks.isArray() && !chunks.isEmpty() && chunks.size() <= 10000);
    for (JsonNode chunk : chunks) {
      require(chunk.isObject());
      text(chunk.path("content"), 100000);
      if (chunk.has("textForEmbedding")) text(chunk.path("textForEmbedding"), 200000);
      if (chunk.has("headingPath")) require(chunk.path("headingPath").isString());
      String primary = text(chunk.path("blockId"), 100);
      List<String> refs = new ArrayList<>();
      if (chunk.has("blockIds")) {
        var ids = chunk.path("blockIds");
        require(ids.isArray() && !ids.isEmpty());
        Set<String> unique = new HashSet<>();
        for (JsonNode id : ids) {
          String value = text(id, 100);
          require(unique.add(value));
          refs.add(value);
        }
        require(primary.equals(refs.get(0)));
      } else refs.add(primary);
      Integer chunkPage = page(chunk.path("pageNumber"));
      for (String ref : refs) {
        JsonNode block = blocks.get(ref);
        require(block != null);
        require(Objects.equals(chunkPage, page(block.path("source").path("page"))));
      }
      // References are unique within one chunk, not across split chunks.
      for (String field : List.of("previousBlockId", "nextBlockId")) {
        var value = chunk.path(field);
        if (!value.isMissingNode() && !value.isNull())
          require(blocks.containsKey(text(value, 100)));
      }
    }
  }
}
