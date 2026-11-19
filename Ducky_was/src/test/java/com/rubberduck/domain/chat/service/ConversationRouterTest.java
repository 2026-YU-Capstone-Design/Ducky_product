package com.rubberduck.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.rubberduck.domain.chat.dto.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

class ConversationRouterTest {
    final ConversationRouter router = new ConversationRouter();

    @Test void intentIsDecidedBeforeEvidenceOrLlm() {
        assertThat(router.decide("큐가 뭐야?", List.of(), false).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("정답 말고 힌트만", List.of(), false).mode()).isEqualTo(ConversationRouter.Mode.HINT);
        assertThat(router.decide("힌트 없이 직접 설명해", List.of(), false).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
        assertThat(router.decide("강의 자료의 큐 설명", List.of(), false).needsDocuments()).isTrue();
        assertThat(router.decide("어떻게?", List.of(), false).mode()).isEqualTo(ConversationRouter.Mode.CLARIFY);
        assertThat(router.decide("왜 CPU가 뜨거워?", List.of(), false).mode()).isEqualTo(ConversationRouter.Mode.DIRECT);
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
}
