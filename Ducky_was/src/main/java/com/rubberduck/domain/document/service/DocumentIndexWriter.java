package com.rubberduck.domain.document.service;

import com.rubberduck.domain.document.entity.*;
import com.rubberduck.domain.document.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Transactional index boundary for the separately owned BM25 integration. */
@Component
@RequiredArgsConstructor
public class DocumentIndexWriter {
  private final DocumentChunkRepository chunks;
  private final SparseEmbeddingService embeddings;

  public void replace(LearningDocument doc, JsonNode result) {
    DocumentResultValidator.validate(result);
    DocumentResultValidator.require(
        doc.getId().toString().equals(result.path("documentId").asText()));
    chunks.deleteByDocument(doc);
    int i = 0;
    for (JsonNode n : result.path("chunks")) {
      String content = n.path("content").asText();
      if (content.isBlank()
          || content.length() > 100000
          || n.path("blockId").asText().length() > 100)
        throw new IllegalArgumentException("INVALID_CHUNK");
      var c =
          DocumentChunk.create(
              doc, i++, content, embeddings.serialize(n.path("textForEmbedding").asText(content)));
      c.setEmbeddingModel(SparseEmbeddingService.MODEL);
      c.setBlockId(n.path("blockId").asText());
      c.setHeadingPath(n.path("headingPath").asText());
      c.setProvenanceJson(n.toString());
      chunks.save(c);
    }
    doc.setChunkCount(i);
    doc.setProcessingWarnings(result.path("warnings").toString());
    doc.setProcessingSeconds(result.path("processingSeconds").asDouble());
  }
}
