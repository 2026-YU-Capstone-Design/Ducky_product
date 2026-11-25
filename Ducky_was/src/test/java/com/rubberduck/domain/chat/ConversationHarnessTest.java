package com.rubberduck.domain.chat;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.chat.dto.MessageResponse;
import com.rubberduck.domain.document.dto.DocumentSearchResult;
import com.rubberduck.domain.document.entity.*;
import com.rubberduck.domain.document.repository.*;
import com.rubberduck.domain.document.service.SparseEmbeddingService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.support.ApiFlowTest;
import jakarta.persistence.EntityManager;
import org.springframework.http.MediaType;
import com.rubberduck.domain.auth.service.AuthTokenService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "chat.response-mode=extractive")
@ActiveProfiles("test")
@Transactional
class ConversationHarnessTest extends ApiFlowTest {
    @Autowired ChatService chat;
    @Autowired LearningDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired SparseEmbeddingService embeddings;
    @Autowired EntityManager em;
    @Autowired AuthTokenService tokens;

    JsonNode apiSend(User user, Long id, String text, List<Long> selection) throws Exception {
        Map<String, Object> payload = new HashMap<>(Map.of("conversationId", id, "message", text, "inputType", "text"));
        if (selection != null) payload.put("documentIds", selection);
        var result = mockMvc.perform(post("/api/chat/messages")
                .header("Authorization", "Bearer " + tokens.issue(user.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(payload)))
                .andExpect(status().isOk()).andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data").path("aiResponse");
    }

    @Test void explicitRequestSelectionRoutesKeywordFreeQuestionThenOmittedScopeDoesNotForceDocuments() throws Exception {
        var user = newUser("attachment-api"); Long id = session(user);
        Long selected = document(user, "Quasar retry budget is seven attempts", true);
        document(user, "Quasar retry budget is forty attempts", true);
        var first = apiSend(user, id, "Quasar retry budget", List.of(selected));
        assertThat(first.path("responseStrategy").asText()).isEqualTo("DOCUMENT");
        assertThat(first.path("sources").size()).isEqualTo(1);
        assertThat(first.path("sources").get(0).path("documentId").asText()).isEqualTo(selected.toString());
        em.flush(); em.clear();
        var general = apiSend(user, id, "파이썬 리스트가 뭐야?", null);
        assertThat(general.path("responseStrategy").asText()).isEqualTo("DIRECT");
        assertThat(general.path("sources").size()).isZero();
        em.flush(); em.clear();
        var resumed = apiSend(user, id, "문서의 Quasar retry budget 다시 설명해", null);
        assertThat(resumed.path("responseStrategy").asText()).isEqualTo("DOCUMENT");
        assertThat(resumed.path("sources").size()).isEqualTo(1);
        assertThat(resumed.path("sources").get(0).path("documentId").asText()).isEqualTo(selected.toString());
        em.flush(); em.clear();
        mockMvc.perform(get("/api/chat/conversations/" + id).header("Authorization", "Bearer " + tokens.issue(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].responseStrategy").value("DOCUMENT"))
                .andExpect(jsonPath("$.data.messages[1].sources[0].documentId").value(selected.toString()))
                .andExpect(jsonPath("$.data.messages[3].responseStrategy").value("DIRECT"))
                .andExpect(jsonPath("$.data.messages[3].sources").isEmpty())
                .andExpect(jsonPath("$.data.messages[5].sources[0].documentId").value(selected.toString()));
    }

    @Test void explicitWeakCandidateAndEmptySelectionKeepEvidenceAndScopeSemantics() throws Exception {
        var user = newUser("weak-selection-api"); Long id = session(user);
        Long selected = document(user, "Quasar retry budget is seven attempts", true);
        var weak = apiSend(user, id, "Quasar founder birth date", List.of(selected));
        assertThat(weak.path("responseStrategy").asText()).isEqualTo("DOCUMENT");
        assertThat(weak.path("sources").size()).isEqualTo(1);
        assertThat(weak.path("content").asText()).contains("근거 충족 여부를 판정하지 않습니다");
        em.flush(); em.clear();
        var restored = chat.getConversation(user, id).messages().get(1);
        assertThat(restored.content()).isEqualTo(weak.path("content").asText());
        assertThat(restored.responseStrategy()).isEqualTo("DOCUMENT");
        assertThat(restored.sources().get(0).documentId()).isEqualTo(selected.toString());
        var cleared = apiSend(user, id, "Quasar retry budget", List.of());
        assertThat(cleared.path("responseStrategy").asText()).isEqualTo("DIRECT");
        assertThat(cleared.path("sources").size()).isZero();
        em.flush(); em.clear();
        var absent = apiSend(user, id, "문서의 Quasar retry budget", null);
        assertThat(absent.path("responseStrategy").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(absent.path("sources").size()).isZero();
    }

    @Test void preservesIndexedPdfPagesInAnswersAndConversationHistory() throws Exception {
        var user = newUser("pdf-source-pages");
        Long id = session(user);
        var pdf = LearningDocument.create(user, "guide", "guide.pdf", "application/pdf", 100, "pdf");
        pdf.markIndexed();
        documents.save(pdf);
        String[] pageContents = {"Quasar retry budget seven attempts", "Zephyr access token expires"};
        for (int i = 0; i < pageContents.length; i++) {
            var chunk = DocumentChunk.create(pdf, i, pageContents[i], embeddings.serialize(pageContents[i]));
            chunk.setEmbeddingModel(SparseEmbeddingService.MODEL);
            chunk.setBlockId("page-" + (i + 1));
            var provenance = new ObjectMapper().createObjectNode().put("pageNumber", i + 1);
            if (i == 0) provenance.putArray("blockIds").add("page-1").add("body-1");
            chunk.setProvenanceJson(provenance.toString());
            chunks.save(chunk);
        }
        for (int i = 0; i < pageContents.length; i++) {
            var response = apiSend(user, id, pageContents[i], List.of(pdf.getId()));
            var source = response.path("sources").get(0);
            assertThat(source.path("documentId").asText()).isEqualTo(pdf.getId().toString());
            assertThat(source.path("pageNumber").asInt()).isEqualTo(i + 1);
            assertThat(source.path("blockIds").size()).isEqualTo(i == 0 ? 2 : 1);
            assertThat(source.path("blockIds").get(0).asText()).isEqualTo("page-" + (i + 1));
        }
        em.flush();
        em.clear();
        mockMvc.perform(get("/api/chat/conversations/" + id)
                        .header("Authorization", "Bearer " + tokens.issue(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].sources[0].pageNumber").value(1))
                .andExpect(jsonPath("$.data.messages[1].sources[0].blockIds[1]").value("body-1"))
                .andExpect(jsonPath("$.data.messages[3].sources[0].pageNumber").value(2));
    }

    @Test void explicitSelectionDoesNotSupplyMissingHintTargetOrMakePendingDocumentSearchable() throws Exception {
        var user = newUser("selection-clarify-api"); Long id = session(user);
        Long selected = document(user, "Quasar retry budget", false);
        var unclear = apiSend(user, id, "힌트 좀 줘", List.of(selected));
        assertThat(unclear.path("responseStrategy").asText()).isEqualTo("CLARIFY");
        assertThat(unclear.path("sources").size()).isZero();
        assertThat(chat.getConversation(user, id).hintCount()).isZero();
        var pending = apiSend(user, id, "Quasar retry budget", List.of(selected));
        assertThat(pending.path("responseStrategy").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(pending.path("responseMode").asText()).isEqualTo("rule");
    }

    @Test void unauthorizedExplicitDocumentSelectionIsRejectedBeforeRouting() throws Exception {
        var user = newUser("selection-owner-api"); Long id = session(user);
        Long foreign = document(newUser("selection-foreign-api"), "Quasar secret", true);
        mockMvc.perform(post("/api/chat/messages").header("Authorization", "Bearer " + tokens.issue(user.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(Map.of(
                        "conversationId", id, "message", "Quasar retry budget", "documentIds", List.of(foreign)))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        assertThat(chat.getConversation(user, id).messageCount()).isZero();
    }

    Long session(User user) {
        return Long.valueOf(chat.startConversation(user, null, null, "test", "test").id());
    }

    Long document(User user, String text, boolean indexed) {
        var doc = LearningDocument.create(user, "policy", "policy.txt", "text/plain", 100, "txt");
        if (indexed) doc.markIndexed();
        documents.save(doc);
        var chunk = DocumentChunk.create(doc, 0, text, embeddings.serialize(text));
        chunk.setEmbeddingModel(SparseEmbeddingService.MODEL);
        chunks.save(chunk);
        return doc.getId();
    }

    @Test void ordinaryQuestionDoesNotAcquireUnrelatedSourcesOrForceHints() {
        var user = newUser("ordinary");
        document(user, "FIFO queue explanation", true);
        var reply = chat.sendMessage(user, session(user), "FIFO queue란?", "text").aiResponse();
        assertThat(reply.type()).isEqualTo("answer");
        assertThat(reply.responseStrategy()).isEqualTo("DIRECT");
        assertThat(reply.sources()).isEmpty();
    }

    @Test void bareDocumentReviewSamplesOnlyTheSelectedIndexedMaterialAndPersistsSources() {
        var user = newUser("portfolio-review");
        Long selected = document(user, "Quasar retry budget is seven attempts.", true);
        Long pending = document(user, "Pending material", false);
        document(user, "Unselected material", true);
        document(newUser("other-review"), "Foreign material", true);

        Long id = session(user);
        var reply = chat.sendMessage(user, id, "피드백해줘", "text", List.of(selected, pending)).aiResponse();
        assertThat(reply.responseStrategy()).isEqualTo("REVIEW");
        assertThat(reply.sources()).extracting(DocumentSearchResult::documentId).containsExactly(selected.toString());
        assertThat(reply.hintNumber()).isNull();
        assertThat(reply.content()).contains("살펴보세요", "검토할 발췌");
        assertRestored(user, id, reply);

        var noTarget = chat.sendMessage(user, session(user), "피드백해줘", "text", List.of()).aiResponse();
        assertThat(noTarget.responseStrategy()).isEqualTo("CLARIFY");
        assertThat(noTarget.sources()).isEmpty();
        var notIndexed = chat.sendMessage(user, session(user), "피드백해줘", "text", List.of(pending)).aiResponse();
        assertThat(notIndexed.responseStrategy()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(notIndexed.sources()).isEmpty();
    }

    @Test void generalIntentAfterDocumentTurnDoesNotSearchOrDiscardStoredSelection() {
        var user = newUser("general-switch"); Long id = session(user);
        Long selected = document(user, "Python list policy", true);
        document(user, "Python list other policy", true);
        chat.sendMessage(user, id, "문서의 Python list 설명", "text", List.of(selected));
        em.flush(); em.clear();
        for (String question : List.of("문서와 관계없이 파이썬 리스트가 뭐야?", "Python list란?")) {
            var reply = chat.sendMessage(user, id, question, "text").aiResponse();
            assertThat(reply.responseStrategy()).isEqualTo("DIRECT");
            assertThat(reply.sources()).isEmpty();
        }
        var resumed = chat.sendMessage(user, id, "문서의 Python list 설명", "text").aiResponse();
        assertThat(resumed.sources()).hasSize(1);
        assertThat(resumed.sources().get(0).documentId()).isEqualTo(selected.toString());
    }

    @Test void missingReferentStaysUnresolvedAfterClarificationsAndDoesNotConsumeHint() {
        var user = newUser("no-referent"); Long id = session(user);
        for (String question : List.of("이거 왜 안 돼?", "이 코드는 왜 오류가 나?", "힌트 좀 줘")) {
            var reply = chat.sendMessage(user, id, question, "text").aiResponse();
            assertThat(reply.responseStrategy()).as(question).isEqualTo("CLARIFY");
            assertThat(reply.type()).isEqualTo("question");
            assertThat(reply.sources()).isEmpty();
        }
        assertThat(chat.requestHint(user, id).hintMessage().responseStrategy()).isEqualTo("CLARIFY");
        assertThat(chat.getConversation(user, id).hintCount()).isZero();
    }

    @Test void nonemptyLexicalCandidateDoesNotEstablishAnswerSupport() {
        var user = newUser("weak-candidate"); Long id = session(user);
        Long selected = document(user, "Quasar retries stop after seven attempts", true);
        var reply = chat.sendMessage(user, id, "문서에서 Quasar 창시자의 생년월일은?", "text", List.of(selected)).aiResponse();
        // Shared token retrieves a real candidate, but it contains no biographical answer.
        assertThat(reply.sources()).isNotEmpty();
        assertThat(reply.content()).contains("후보가 있다는 것만으로", "근거 충족 여부를 판정하지 않습니다");
        assertThat(reply.responseMode()).isEqualTo("extractive");
        assertThat(reply.responseStrategy()).isEqualTo("DOCUMENT"); // Intent, not a supported-answer verdict.
        assertThat(reply.sources().get(0).documentId()).isEqualTo(selected.toString());
        assertThat(reply.content()).startsWith("검색 후보 발췌입니다.").contains("Quasar retries stop after seven attempts");
        assertRestored(user, id, reply);
    }

    @Test void weakCandidatesRemainUnverifiedInChatHintsAndHintEndpointAfterReload() {
        var user = newUser("weak-hint"); Long id = session(user);
        Long selected = document(user, "Zephyr API retries are enabled after network failure", true);
        document(user, "Zephyr API authentication secret expires after 300 seconds", true);
        // The selected retry policy cannot establish authentication-token expiry.
        var reply = chat.sendMessage(user, id, "문서의 Zephyr API 인증 토큰 만료 시간 힌트만 줘", "text", List.of(selected)).aiResponse();
        assertUnverifiedHint(reply, selected, 1);
        assertRestored(user, id, reply);
        var next = chat.requestHint(user, id).hintMessage();
        assertUnverifiedHint(next, selected, 2);
        assertRestored(user, id, next);
        assertThat(chat.getConversation(user, id).hintCount()).isEqualTo(2);
    }

    private void assertUnverifiedHint(MessageResponse reply, Long selected, int number) {
        assertThat(reply.sources()).hasSize(1);
        assertThat(reply.sources().get(0).documentId()).isEqualTo(selected.toString());
        assertThat(reply.content()).contains("후보가 있다는 것만으로", "근거 충족 여부를 판정하지 않습니다")
                .doesNotContain("300 seconds", "authentication secret");
        assertThat(reply.responseStrategy()).isEqualTo("HINT");
        assertThat(reply.responseMode()).isEqualTo("extractive");
        assertThat(reply.type()).isEqualTo("hint");
        assertThat(reply.hintNumber()).isEqualTo(number);
    }

    private void assertRestored(User user, Long id, MessageResponse expected) {
        em.flush(); em.clear();
        var restored = chat.getConversation(user, id).messages().stream()
                .filter(message -> expected.id().equals(message.id())).findFirst().orElseThrow();
        assertThat(restored.content()).isEqualTo(expected.content());
        assertThat(restored.sources()).isEqualTo(expected.sources());
        assertThat(restored.responseStrategy()).isEqualTo(expected.responseStrategy());
        assertThat(restored.responseMode()).isEqualTo(expected.responseMode());
        assertThat(restored.type()).isEqualTo(expected.type());
        assertThat(restored.hintNumber()).isEqualTo(expected.hintNumber());
    }

    @Test void selectedScopeAndSourcesSurviveReloadAndFollowUp() {
        var user = newUser("scope");
        Long selected = document(user, "Quasar retries stop after seven attempts", true);
        document(user, "Quasar retries private distractor", true);
        document(newUser("foreign"), "Quasar retries secret", true);
        document(user, "Quasar retries pending", false);
        Long id = session(user);
        var reply = chat.sendMessage(user, id, "Quasar retries 문서 설명", "text", List.of(selected)).aiResponse();
        assertThat(reply.sources()).hasSize(1);
        assertThat(reply.sources().get(0).documentId()).isEqualTo(selected.toString());
        em.flush(); em.clear();
        var restored = chat.getConversation(user, id).messages().get(1);
        assertThat(restored.sources()).isEqualTo(reply.sources());
        assertThat(restored.responseStrategy()).isEqualTo("DOCUMENT");
        var followUp = chat.sendMessage(user, id, "그건 왜?", "text").aiResponse();
        assertThat(chat.sendMessage(user, id, "더 설명해", "text").aiResponse().sources()).hasSize(1);
        assertThat(followUp.sources()).hasSize(1);
        assertThat(followUp.sources().get(0).documentId()).isEqualTo(selected.toString());
    }

    @Test void missingEvidenceNeverBecomesGeneralKnowledgeOrInventedCitation() {
        var user = newUser("missing");
        var reply = chat.sendMessage(user, session(user), "문서에서 Zyxwvu 근거는?", "text").aiResponse();
        assertThat(reply.responseStrategy()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(reply.content()).contains("근거를 찾지 못했습니다");
        assertThat(reply.sources()).isEmpty();
        assertThat(reply.responseMode()).isEqualTo("rule");
    }

    @Test void emptySelectionDoesNotBroadenRetrievalAndHintEndpointHonorsScope() {
        var user = newUser("empty"); Long id = session(user);
        Long doc = document(user, "Quasar retries stop after seven attempts", true);
        chat.sendMessage(user, id, "Quasar retries 문서 설명", "text", List.of(doc));
        assertThat(chat.requestHint(user, id).hintMessage().sources()).hasSize(1);
        var cleared = chat.sendMessage(user, id, "Quasar retries 문서 설명", "text", List.of()).aiResponse();
        assertThat(cleared.sources()).isEmpty();
        assertThat(cleared.responseStrategy()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(chat.sendMessage(user, id, "그건 왜?", "text").aiResponse().sources()).isEmpty();
    }

    @Test void foreignSelectionAndConversationAreRejected() {
        var user = newUser("owner"); var other = newUser("other");
        Long foreign = document(other, "secret", true);
        Long id = session(user);
        assertThatThrownBy(() -> chat.sendMessage(user, id, "문서 설명", "text", List.of(foreign)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> chat.sendMessage(other, id, "설명", "text"))
                .isInstanceOf(CustomException.class);
    }

    @Test void hintsAdvanceAcrossChatAndHintEndpointAndCanReturnToAnswer() {
        var user = newUser("hint"); Long id = session(user);
        var first = chat.sendMessage(user, id, "정렬 힌트만 줘", "text").aiResponse();
        var second = chat.sendMessage(user, id, "다음은?", "text").aiResponse();
        var third = chat.requestHint(user, id).hintMessage();
        assertThat(first.hintNumber()).isEqualTo(1);
        assertThat(second.hintNumber()).isEqualTo(2);
        assertThat(third.hintNumber()).isEqualTo(3);
        assertThat(second.content()).isNotEqualTo(first.content());
        var feature = chat.sendMessage(user, id, "힌트 기능이 뭐야? 기능 자체만 짧게 설명해줘.", "text").aiResponse();
        assertThat(feature.responseStrategy()).isEqualTo("DIRECT");
        assertThat(feature.type()).isEqualTo("answer");
        assertThat(feature.hintNumber()).isNull();
        assertThat(chat.getConversation(user, id).hintCount()).isEqualTo(3);
        assertThat(chat.sendMessage(user, id, "힌트 말고 정답을 설명해", "text").aiResponse().type()).isEqualTo("answer");
    }

    @Test void ambiguityAsksOneFocusedQuestionAndCompletionWordDoesNotEndQuestion() {
        var user = newUser("ambiguous"); Long id = session(user);
        assertThat(chat.sendMessage(user, id, "이거 왜?", "text").aiResponse().type()).isEqualTo("question");
        chat.sendMessage(user, id, "완료 큐가 뭐야?", "text");
        assertThat(chat.getConversation(user, id).status()).isEqualTo("in_progress");
    }
}
