package com.rubberduck.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.rubberduck.domain.chat.dto.SendMessageRequest;
import com.rubberduck.domain.chat.entity.ChatMessage;
import tools.jackson.databind.ObjectMapper;

class ConversationRouterTest {
    final ConversationRouter router = new ConversationRouter();

    @Test void currentRequestDocumentContextSupportsKeywordFreeQuestionButNotPermanentForcing() {
        assertThat(router.decide("Quasar retry budget", List.of(), true).mode()).isEqualTo(ConversationRouter.Mode.DOCUMENT);
        assertThat(router.decide("파이썬 리스트가 뭐야?", List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("문서와 관계없이 파이썬 리스트가 뭐야?", List.of(), true).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("힌트 좀 줘", List.of(), true).mode()).isEqualTo(ConversationRouter.Mode.CLARIFY);
        assertThat(router.decide("Quasar retry budget 힌트만 줘", List.of(), true).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("Quasar retry budget 힌트만 줘", List.of(), true).needsDocuments()).isTrue();
    }

    @Test void documentScopeDoesNotOverrideExplicitOrIndependentGeneralIntent() {
        var previousQuestion = ChatMessage.create(null, "user", "문서의 큐를 설명해", "question", "text", 1);
        var previousAnswer = ChatMessage.create(null, "assistant", "문서 발췌", "answer", "system", 2);
        previousAnswer.setResponseStrategy("DOCUMENT");
        var history = List.of(previousQuestion, previousAnswer);
        for (String question : List.of("문서와 관계없이 파이썬 리스트가 뭐야?", "자료 말고 일반적으로 파이썬 리스트 설명해", "파이썬 리스트가 뭐야?")) {
            var decision = router.decide(question, history);
            assertThat(decision.mode()).as(question).isEqualTo(ConversationRouter.Mode.DIRECT);
            assertThat(decision.needsDocuments()).as(question).isFalse();
        }
        assertThat(router.decide("그건 왜?", history).mode()).isEqualTo(ConversationRouter.Mode.DOCUMENT);
    }

    @Test void missingReferentAndBareHintNeedClarification() {
        for (String question : List.of("이거 왜 안 돼?", "이거\n왜 안 돼?", "이 코드는 왜 오류가 나?", "힌트 좀 줘")) {
            assertThat(router.decide(question, List.of()).mode()).as(question).isEqualTo(ConversationRouter.Mode.CLARIFY);
        }
        for (String term : List.of("AI", "OS", "DB", "hi", "JS", "Go", "R")) {
            assertThat(router.decide(term, List.of()).mode()).as(term).isEqualTo(ConversationRouter.Mode.DIRECT);
        }
    }

    @Test void providedTopicAndCodeResolveHintTargetWithoutForcingClarification() {
        var topic = ChatMessage.create(null, "user", "파이썬 리스트 인덱스를 설명해", "question", "text", 1);
        assertThat(router.decide("힌트 좀 줘", List.of(topic)).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("이거 왜 안 돼?", List.of(topic)).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        var code = ChatMessage.create(null, "user", "이 코드는 왜 오류가 나?\n```python\nprint(missing)\n```", "question", "text", 1);
        assertThat(router.decide(code.getMessageText(), List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("힌트 좀 줘", List.of(code)).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("문서와 관계없이 정렬 힌트만 줘", List.of()).mode()).isEqualTo(ConversationRouter.Mode.HINT);
    }

    @Test void intentIsDecidedBeforeEvidenceOrLlm() {
        assertThat(router.decide("큐가 뭐야?", List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("큐 문제의 정답 말고 힌트만", List.of()).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("힌트 없이 직접 설명해", List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("강의 자료의 큐 설명", List.of()).needsDocuments()).isTrue();
        assertThat(router.decide("어떻게?", List.of()).mode()).isEqualTo(ConversationRouter.Mode.CLARIFY);
        assertThat(router.decide("왜 CPU가 뜨거워?", List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
    }

    @Test void featureAndSocialQuestionsDoNotInheritHintOrDocumentMode() {
        var topic = ChatMessage.create(null, "user", "인덱스 오류를 설명해", "question", "text", 1);
        var hint = ChatMessage.create(null, "assistant", "인덱스 범위를 확인해 보세요.", "hint", "system", 2);
        var document = ChatMessage.create(null, "assistant", "문서 출처", "answer", "system", 2);
        document.setResponseStrategy("DOCUMENT");

        for (var history : List.of(List.of(topic, hint), List.of(topic, document))) {
            for (String question : List.of("그럼 너 누구야?", "고마워!", "힌트 기능이 뭐야? 기능 자체만 짧게 설명해줘.")) {
                var decision = router.decide(question, history, true);
                assertThat(decision.mode()).as(question).isEqualTo(ConversationRouter.Mode.DIRECT);
                assertThat(decision.needsDocuments()).as(question).isFalse();
                assertThat(decision.simpleIntent()).as(question).isNotNull();
            }
        }
        assertThat(router.decide("다음은?", List.of(topic, hint)).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("그럼 파이썬 리스트가 뭐야?", List.of(topic, hint)).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("그건 왜?", List.of(topic, document)).mode()).isEqualTo(ConversationRouter.Mode.DOCUMENT);
    }

    @Test void requestForOneClueCanGuideLearningWithoutUsingTheWordHint() {
        var decision = router.decide("인덱스 오류를 혼자 찾아보고 싶어. 확인할 단서 하나만 줘.", List.of());
        assertThat(decision.mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(decision.simpleIntent()).isNull();
    }

    @Test void existingRequestAliasesRemainCompatibleAndSelectionIsOptional() {
        var mapper = new ObjectMapper();
        var old = mapper.readValue("{\"conversation_id\":1,\"message\":\"hello\",\"input_type\":\"text\"}", SendMessageRequest.class);
        assertThat(old.effectiveMessageText()).isEqualTo("hello");
        assertThat(old.documentIds()).isNull();
        var selected = mapper.readValue("{\"conversation_id\":1,\"message_text\":\"doc\",\"document_ids\":[4]}", SendMessageRequest.class);
        assertThat(selected.documentIds()).containsExactly(4L);
        var empty = mapper.readValue("{\"documentIds\":[]}", SendMessageRequest.class);
        assertThat(empty.documentIds()).isEmpty();
    }

    @Test void reviewAndExerciseCoachingAreDistinctFromDefinitions() {
        assertThat(router.decide("그럼 피드백해줘", List.of()).mode()).isEqualTo(ConversationRouter.Mode.CLARIFY);
        var review = router.decide("피드백해줘", List.of(), true);
        assertThat(review.mode()).isEqualTo(ConversationRouter.Mode.REVIEW);
        assertThat(review.needsDocuments()).isTrue();
        assertThat(router.decide("피드백이 뭐야?", List.of()).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("이 연습 문제를 풀어줘. 2x + 3 = 9", List.of()).mode())
                .isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("이 문제 풀어줘. 힌트 없이 정답만 알려줘.", List.of()).mode())
                .isEqualTo(ConversationRouter.Mode.DIRECT);
    }
}
