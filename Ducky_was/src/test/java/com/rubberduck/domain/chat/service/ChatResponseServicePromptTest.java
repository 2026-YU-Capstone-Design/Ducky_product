package com.rubberduck.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.user.entity.User;

class ChatResponseServicePromptTest {

    private final ChatResponseService chatResponseService = new ChatResponseService("", "test-model", 1);

    @Test
    void turnPromptIncludesCurrentConversationHistoryAndCurrentInput() {
        User user = userWithLearningStyle("active", "visual", "sequential");
        Conversation conversation = Conversation.start(user, null, null, "정렬 공부", "정렬 알고리즘");
        List<ChatMessage> history = List.of(
                ChatMessage.create(conversation, "user", "교환 정렬이 뭐야?", "question", "text", 1),
                ChatMessage.create(conversation, "assistant", "서로 인접한 값을 비교해 바꾸는 방식이야.", "question", "system", 2)
        );

        String prompt = chatResponseService.buildTurnPrompt(
                "다른 정렬과 어떻게 달라?",
                user,
                "",
                history,
                null
        );

        assertThat(prompt)
                .contains("현재 세션 전체 대화 기록")
                .contains("교환 정렬이 뭐야?")
                .contains("서로 인접한 값을 비교")
                .contains("현재 사용자 입력")
                .contains("다른 정렬과 어떻게 달라?");
    }

    @Test
    void turnPromptKeepsShortFollowUpQuestionsConnectedToPriorContext() {
        User user = userWithLearningStyle("reflective", "verbal", "sequential");
        Conversation conversation = Conversation.start(user, null, null, "서버 운영", "PM2");
        List<ChatMessage> history = List.of(
                ChatMessage.create(conversation, "user", "서버에서 pm2로 다시 실행하려면 어떻게 해?", "question", "text", 1),
                ChatMessage.create(conversation, "assistant", "pm2 restart 애플리케이션_이름 또는 pm2 restart all을 쓰면 돼.", "answer", "system", 2)
        );

        String prompt = chatResponseService.buildTurnPrompt(
                "이름을 몰라",
                user,
                "",
                history,
                null
        );

        assertThat(prompt)
                .contains("pm2 restart")
                .contains("애플리케이션_이름")
                .contains("이름을 몰라")
                .contains("짧은 후속 질문은 현재 세션 전체 대화 기록에서 지시 대상을 찾아 답한다");
    }

    @Test
    void learningTypeStructureAliasOverridesUnderstandingForDuckRequests() {
        User user = userWithLearningStyle("active", "visual", "sequential");

        String prompt = chatResponseService.buildTurnPrompt(
                "큐가 뭐야?",
                user,
                "",
                List.of(),
                Map.of(
                        "processing", "reflective",
                        "expression", "verbal",
                        "structure", "global"
                )
        );

        assertThat(prompt)
                .contains("- 처리 방식: reflective")
                .contains("- 표현 선호: verbal")
                .contains("- 이해 구조: global")
                .contains("전체 그림을 먼저 잡고 세부 단계로 내려간다");
    }

    @Test
    void hintPromptIncludesConversationHistoryPreviousHintsAndLabelRule() {
        User user = userWithLearningStyle("active", "visual", "global");
        Conversation conversation = Conversation.start(user, null, null, "정렬 공부", "정렬 알고리즘");
        ChatMessage firstHint = ChatMessage.create(
                conversation,
                "assistant",
                "비교가 일어나는 위치를 먼저 표시해보세요.",
                "hint",
                "system",
                3
        );
        firstHint.setHintNumber(1);

        String prompt = chatResponseService.buildHintPrompt(
                user,
                conversation,
                2,
                List.of(
                        ChatMessage.create(conversation, "user", "장점 FIFO 같아", "question", "text", 1),
                        firstHint
                ),
                List.of(firstHint)
        );

        assertThat(prompt)
                .contains("현재 세션 전체 대화 기록")
                .contains("장점 FIFO 같아")
                .contains("이미 제공한 힌트")
                .contains("비교가 일어나는 위치")
                .contains("\"힌트 1:\" 같은 번호, 제목, 머리말을 붙이지 마");
    }

    @Test
    void formatterOnlyUsesMessagesProvidedForTheCurrentConversation() {
        User user = userWithLearningStyle("active", "visual", "sequential");
        Conversation currentConversation = Conversation.start(user, null, null, "현재", "교환 정렬");
        Conversation otherConversation = Conversation.start(user, null, null, "다른 세션", "퀵 정렬");
        ChatMessage currentMessage = ChatMessage.create(
                currentConversation,
                "user",
                "교환 정렬 설명",
                "question",
                "text",
                1
        );
        ChatMessage otherMessage = ChatMessage.create(
                otherConversation,
                "user",
                "퀵 정렬 설명",
                "question",
                "text",
                1
        );

        String prompt = chatResponseService.buildTurnPrompt(
                "더 설명해줘",
                user,
                "",
                List.of(currentMessage),
                null
        );

        assertThat(prompt)
                .contains("교환 정렬 설명")
                .doesNotContain(otherMessage.getMessageText());
    }

    private User userWithLearningStyle(String processing, String expression, String understanding) {
        User user = User.create("Tester", "tester@example.com", "tester", "password");
        user.setProcessingStyle(processing);
        user.setExpressionStyle(expression);
        user.setUnderstandingStyle(understanding);
        return user;
    }
}
