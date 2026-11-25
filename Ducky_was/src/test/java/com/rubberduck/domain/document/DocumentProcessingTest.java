package com.rubberduck.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.rubberduck.domain.chat.service.ChatResponseService;
import com.rubberduck.domain.document.repository.DocumentChunkRepository;
import com.rubberduck.domain.document.repository.LearningDocumentRepository;
import com.rubberduck.domain.document.service.*;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.support.ApiFlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"document.pipeline-enabled=true", "document.poll-interval-ms=3600000",
        "document.storage-root=./build/test-documents", "chat.response-mode=extractive"})
@ActiveProfiles("test")
class DocumentProcessingTest extends ApiFlowTest {
    @Autowired DocumentService service;
    @Autowired DocumentProcessingService processing;
    @Autowired LearningDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired LocalDocumentStore store;
    @MockitoBean DocumentProcessorClient worker;
    @MockitoBean ChatResponseService chatResponse;
    final byte[] data = "Quasar retries stop after seven attempts.".getBytes(StandardCharsets.UTF_8);

    JsonNode result(Long id) throws Exception {
        return new ObjectMapper().valueToTree(Map.of(
                "schemaVersion", "ducky-document-v1", "documentId", id.toString(),
                "sourceSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),
                "markdown", new String(data, StandardCharsets.UTF_8),
                "canonicalBlocks", List.of(Map.of("doc_id", id.toString(), "block_id", "block-1")),
                "chunks", List.of(Map.of("content", new String(data, StandardCharsets.UTF_8),
                        "blockId", "block-1", "headingPath", "Retry policy")),
                "warnings", List.of(), "processingSeconds", 0.1));
    }

    @Test
    void completionIsIdempotentAndSourcesAreScopedToOwner() throws Exception {
        var owner = newUser("processor");
        var other = newUser("stranger");
        var uploaded = service.upload(owner, new MockMultipartFile("file", "../same.txt", "text/plain", data));
        Long id = Long.valueOf(uploaded.id());
        assertThat(uploaded.indexingStatus()).isEqualTo("pending");
        assertThat(uploaded.fileName()).isEqualTo("same.txt");
        when(worker.process(eq(id), eq("txt"), any())).thenReturn(result(id));
        processing.process(id);
        processing.process(id);
        verify(worker, times(1)).process(eq(id), eq("txt"), any());
        assertThat(service.get(owner, id).indexingStatus()).isEqualTo("indexed");
        assertThat(service.get(owner, id).chunkCount()).isEqualTo(1);
        assertThat(service.searchResults(owner, "Quasar retries", 3)).hasSize(1);
        assertThat(service.searchResults(other, "Quasar retries", 3)).isEmpty();
        assertThatThrownBy(() -> service.search(other, "Quasar", 3, List.of(id))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.delete(other, id)).isInstanceOf(CustomException.class);
        service.delete(owner, id);
        assertThat(service.searchResults(owner, "Quasar", 3)).isEmpty();
    }

    @Test
    void koreanQueryRecoversAnEnglishSourceWithoutChangingDocumentScope() throws Exception {
        var owner = newUser("translation");
        var other = newUser("translation-outsider");
        Long id = Long.valueOf(service.upload(owner, new MockMultipartFile("file", "policy.txt", "text/plain", data)).id());
        when(worker.process(eq(id), eq("txt"), any())).thenReturn(result(id));
        processing.process(id);
        String query = "최대 몇 번까지 다시 시도하나요?";
        when(chatResponse.translateDocumentQuery(query)).thenReturn("How many times can retries be attempted at most?");

        assertThatThrownBy(() -> service.search(other, query, 3, List.of(id))).isInstanceOf(CustomException.class);
        assertThat(service.search(owner, query, 3, List.of()).results()).isEmpty();
        verifyNoInteractions(chatResponse);
        assertThat(service.search(owner, query, 3, List.of(id)).results()).singleElement()
                .satisfies(source -> assertThat(source.documentId()).isEqualTo(id.toString()));
        verify(chatResponse).translateDocumentQuery(query);

        when(chatResponse.translateDocumentQuery(query)).thenReturn("");
        assertThat(service.search(owner, query, 3, List.of(id)).results()).isEmpty();
    }

    @Test
    void wrongDocumentResultNeverBecomesSearchableAndCanRetry() throws Exception {
        var owner = newUser("mismatch");
        Long id = Long.valueOf(service.upload(owner, new MockMultipartFile("file", "retry.txt", "text/plain", data)).id());
        when(worker.process(eq(id), eq("txt"), any())).thenReturn(result(id + 100));
        processing.process(id);
        assertThat(service.get(owner, id).processingError()).isEqualTo("INVALID_PROCESSING_RESULT");
        assertThat(service.get(owner, id).retryAllowed()).isTrue();
        assertThat(service.searchResults(owner, "Quasar", 3)).isEmpty();
        assertThat(service.retry(owner, id).indexingStatus()).isEqualTo("pending");
        when(worker.process(eq(id), eq("txt"), any())).thenReturn(result(id));
        processing.process(id);
        assertThat(service.get(owner, id).indexingStatus()).isEqualTo("indexed");
        service.delete(owner, id);
    }

    @Test
    void deletionDuringConversionCannotResurrectDocument() throws Exception {
        var owner = newUser("delete-race");
        Long id = Long.valueOf(service.upload(owner, new MockMultipartFile("file", "race.txt", "text/plain", data)).id());
        when(worker.process(eq(id), eq("txt"), any())).thenAnswer(invocation -> {
            service.delete(owner, id);
            return result(id);
        });
        processing.process(id);
        assertThat(documents.findById(id)).isEmpty();
        assertThat(service.searchResults(owner, "Quasar", 3)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missingBlock","duplicateCanonical","contentNumber","embeddingNull"})
    void invalidHttpResultNeverBecomesSearchableAndValidRetryStillWorks(String mutation) throws Exception {
        var owner=newUser("httpSemantic");
        Long id=Long.valueOf(service.upload(owner,new MockMultipartFile("file","local.txt","text/plain",data)).id());
        var bad=(tools.jackson.databind.node.ObjectNode)result(id);
        var chunk=(tools.jackson.databind.node.ObjectNode)bad.path("chunks").get(0);
        switch(mutation){
          case "missingBlock" -> chunk.put("blockId","missing");
          case "duplicateCanonical" -> ((tools.jackson.databind.node.ArrayNode)bad.path("canonicalBlocks")).add(bad.path("canonicalBlocks").get(0).deepCopy());
          case "contentNumber" -> chunk.put("content",42);
          case "embeddingNull" -> chunk.putNull("textForEmbedding");
        }
        when(worker.process(eq(id),eq("txt"),any())).thenReturn(bad);
        processing.process(id);
        assertThat(service.get(owner,id).indexingStatus()).isEqualTo("failed");
        assertThat(service.get(owner,id).processingError()).isEqualTo("INVALID_PROCESSING_RESULT");
        assertThat(service.searchResults(owner,"Quasar",5)).isEmpty();
        service.retry(owner,id);when(worker.process(eq(id),eq("txt"),any())).thenReturn(result(id));processing.process(id);
        assertThat(service.get(owner,id).indexingStatus()).isEqualTo("indexed");
    }

    @Test
    void workerFailureLeavesDurableSourceAndExplicitError() throws Exception {
        var owner = newUser("failure");
        Long id = Long.valueOf(service.upload(owner, new MockMultipartFile("file", "scan.pdf", "application/pdf", data)).id());
        when(worker.process(eq(id), eq("pdf"), any())).thenThrow(new IllegalStateException("OCR_REQUIRED"));
        processing.process(id);
        assertThat(service.get(owner, id).processingError()).isEqualTo("OCR_REQUIRED");
        assertThat(store.read(documents.findById(id).orElseThrow().getSourceKey())).isEqualTo(data);
        service.delete(owner, id);
    }
}
