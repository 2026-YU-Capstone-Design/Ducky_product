package com.rubberduck.domain.chat;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.rubberduck.domain.chat.service.ChatService;
import com.rubberduck.domain.document.entity.*;
import com.rubberduck.domain.document.repository.*;
import com.rubberduck.domain.document.service.SparseEmbeddingService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.support.ApiFlowTest;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "chat.response-mode=extractive")
@ActiveProfiles("test")
@Transactional
class ConversationHarnessTest extends ApiFlowTest {
    @Autowired ChatService chat;
    @Autowired LearningDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired SparseEmbeddingService embeddings;
    @Autowired EntityManager em;

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
        assertThat(chat.sendMessage(user, id, "힌트 말고 정답을 설명해", "text").aiResponse().type()).isEqualTo("answer");
    }

    @Test void ambiguityAsksOneFocusedQuestionAndCompletionWordDoesNotEndQuestion() {
        var user = newUser("ambiguous"); Long id = session(user);
        assertThat(chat.sendMessage(user, id, "이거 왜?", "text").aiResponse().type()).isEqualTo("question");
        chat.sendMessage(user, id, "완료 큐가 뭐야?", "text");
        assertThat(chat.getConversation(user, id).status()).isEqualTo("in_progress");
    }
}
