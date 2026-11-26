package com.rubberduck.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.document.dto.DocumentSearchResult;
import com.rubberduck.domain.user.entity.User;
import tools.jackson.databind.ObjectMapper;

class ChatResponseServicePromptTest {

    private final ChatResponseService chatResponseService = new ChatResponseService("", "test-model", 1);

    @Test
    void debugReportStartsWithOneQuestionWithoutCallingTheModel() {
        var reply = chatResponseService.generateTurn("리스트를 반복하다가 IndexError가 나.", null, "", List.of());
        assertThat(reply.type()).isEqualTo("question");
        assertThat(reply.responseMode()).isEqualTo("rule");
        assertThat(reply.content()).isEqualTo("오류가 난 순간에 인덱스 값은 얼마였나요?");

        var assistant = ChatMessage.create(null, "assistant", reply.content(), "question", "system", 1);
        assistant.setResponseStrategy("DEBUG");
        var followUp = chatResponseService.generateTurn("인덱스 값은 3이야", null, "", List.of(assistant));
        assertThat(followUp.type()).isEqualTo("question");
        assertThat(followUp.content()).isEqualTo("그 값이 정해지기 직전에 어떤 조건을 확인했나요?");
        assertThat(chatResponseService.buildTurnPrompt("인덱스 값은 3이야", null, "", List.of(assistant), null))
                .contains("질문 한 문장만", "이미 답한 내용을 다시 묻지 않는다", reply.content());
    }

    @Test
    void groundedAnswerKeepsOnlySourcesTheModelSelected() {
        var candidates = List.of(
                new DocumentSearchResult("4", "5", "Guide", "Intro", 0.4, "first", "Introduction", 1, List.of("first")),
                new DocumentSearchResult("4", "6", "Guide", "Answer", 0.3, "second", "Details", 2, List.of("second")));
        var reply = chatResponseService.parseGroundedReply(
                """
                {"answer":"**Seven attempts.**","source_chunk_ids":["6"]}
                """, "answer", candidates);
        assertThat(reply.content()).isEqualTo("**Seven attempts.**");
        assertThat(reply.sources()).containsExactly(candidates.get(1));
    }

    @Test
    void groundedFormatAcceptsOnlyCandidateChunkIds() {
        var candidates = List.of(
                new DocumentSearchResult("4", "57", "Guide", "Details", 0.4, "alpha", "Intro", 2, List.of("alpha")),
                new DocumentSearchResult("4", "143", "Guide", "Answer", 0.3, "beta", "Details", 3, List.of("beta")));
        var format = new ObjectMapper().valueToTree(ChatResponseService.groundedResponseFormat(candidates));
        var allowedIds = format.at("/format/schema/properties/source_chunk_ids/items/enum");
        assertThat(allowedIds.size()).isEqualTo(2);
        assertThat(allowedIds.get(0).asText()).isEqualTo("57");
        assertThat(allowedIds.get(1).asText()).isEqualTo("143");
    }

    @Test
    void missingOrUnknownEvidenceDoesNotProduceCitationsOrUnverifiedHints() {
        var candidates = List.of(
                new DocumentSearchResult("4", "5", "Guide", "Intro", 0.4, "first", "", 1, List.of("first")));
        for (String json : List.of(
                """
                {"answer":"Unsupported claim.","source_chunk_ids":[]}
                """,
                """
                {"answer":"Unsupported claim.","source_chunk_ids":["5","99"]}
                """)) {
            var reply = chatResponseService.parseGroundedReply(json, "hint", candidates);
            assertThat(reply.sources()).isEmpty();
            assertThat(reply.type()).isEqualTo("answer");
            assertThat(reply.content()).contains("근거를 찾지 못했습니다").doesNotContain("Unsupported claim");
        }
    }

    @Test
    void olderStoredSourceRetainsItsPrimaryBlock() {
        var source = new ObjectMapper().readValue(
                """
                {"documentId":"4","chunkId":"5","title":"Guide","content":"Legacy excerpt","score":0.25,
                 "blockId":"legacy-block","headingPath":"Details","pageNumber":1}
                """, DocumentSearchResult.class);
        assertThat(source.blockIds()).containsExactly("legacy-block");
    }

    @Test
    void generalIntentExcludesStaleCandidatesAndWeakCandidatesRequireSupportJudgment() {
        User user = userWithLearningStyle("active", "visual", "sequential");
        String candidate = "[문서 4, 청크 5] Quasar retries stop after seven attempts.";
        assertThat(chatResponseService.buildTurnPrompt("문서와 관계없이 파이썬 리스트가 뭐야?", user, candidate, List.of(), null))
                .contains("직접 답변이다").doesNotContain("Quasar retries");
        // This checks the instruction only, not whether a real LLM obeys it.
        assertThat(chatResponseService.buildTurnPrompt("문서에서 Quasar 창시자의 생년월일은?", user, candidate, List.of(), null))
                .contains(candidate).contains("근거가 질문에 답하지 못하면 근거 부족이라고 답한다")
                .contains("출처 ID를 만들지 않는다");
    }

    @Test
    void shortEnglishFragmentsAskForClarificationInsteadOfGuessingAcronyms() {
        ChatResponseService.AiReply reply = chatResponseService.generateTurn(
                "gd",
                userWithLearningStyle("active", "visual", "sequential"),
                "",
                List.of(),
                null
        );

        assertThat(reply.type()).isEqualTo("question");
        assertThat(reply.content())
                .contains("입력이 너무 짧아서")
                .doesNotContain("Google")
                .doesNotContain("Graphic");
    }

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
                .contains("짧은 후속 질문은 현재 세션 전체 대화 기록에서 지시 대상을 찾되");
    }

    @Test
    void factualQuestionGetsDirectAnswerGuidance() {
        String prompt = chatResponseService.buildTurnPrompt(
                "교환 정렬이 뭐야?",
                userWithLearningStyle("active", "visual", "sequential"),
                "",
                List.of(),
                null
        );

        assertThat(prompt)
                .contains("직접 답변이다")
                .contains("정보가 부족한 경우에만")
                .doesNotContain("정답, 결론, 해결책, 수정 방법을 알려주지 마", "현재 힌트 단계:");
    }

    @Test
    void explicitHintAndDocumentQuestionUseDifferentGuidance() {
        User user = userWithLearningStyle("active", "visual", "sequential");
        assertThat(chatResponseService.buildTurnPrompt("정렬 힌트만 줘", user, "", List.of(), null))
                .contains("힌트 요청이다", "질문 1개", "현재 힌트 단계: 1");
        assertThat(chatResponseService.buildTurnPrompt("업로드 제한은?", user, "[문서 4, 청크 5] 20MB", List.of(), null))
                .contains("문서 근거 답변이다").contains("실제 사용한 발췌만 근거로 선택한다")
                .contains("신뢰할 수 없는 참고 자료").contains("확인 필요");
    }

    @Test
    void productHintQuestionAnswersDirectlyWithoutGeneratingAHint() {
        var user = userWithLearningStyle("active", "visual", "sequential");
        var reply = chatResponseService.generateTurn("힌트 기능이 뭐야? 기능 자체만 짧게 설명해줘.", user, "", List.of());
        assertThat(reply.type()).isEqualTo("answer");
        assertThat(reply.responseMode()).isEqualTo("rule");
        assertThat(reply.content()).contains("단서").doesNotContain("?", "다음으로");
    }

    @Test
    void reviewGuidanceReferencesExcerptsAndLetsTheUserChooseTheNextEdit() {
        var user = userWithLearningStyle("active", "visual", "sequential");
        String excerpt = "[문서 1, 청크 2] The draft states a 20% improvement.";
        assertThat(chatResponseService.buildTurnPrompt("첨부 PDF 피드백해줘", user, excerpt, List.of(), null))
                .contains("피드백 요청이다", "확인한 발췌 범위", "완성 답안이나 전체 수정본", "확인할 단서", excerpt)
                .doesNotContain("현재 힌트 단계");
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
                .contains("직접 답변을 금지하는 이유가 아니다");
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
                .contains("range(n)의 경우 0부터 n-1까지")
                .contains("이 부분에서 오류가 발생했을 수 있습니다")
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
