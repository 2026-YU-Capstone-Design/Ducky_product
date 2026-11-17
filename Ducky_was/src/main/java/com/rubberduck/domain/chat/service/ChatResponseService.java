package com.rubberduck.domain.chat.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;
import com.rubberduck.domain.chat.entity.ChatMessage;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

@Service
public class ChatResponseService {

    public record AiReply(String content, String type) {
    }

    public record LearningStyleContext(String processing, String expression, String understanding) {
    }

    private enum ReplyMode {
        DIRECT_ANSWER,
        RUBBER_DUCK
    }

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public ChatResponseService(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.chat-model:gpt-4.1-mini}") String model,
            @Value("${openai.request-timeout-seconds:30}") long timeoutSeconds
    ) {
        this.webClient = WebClient.builder().baseUrl("https://api.openai.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public AiReply generateTurn(
            String userText,
            User user,
            String documentContext,
            List<ChatMessage> conversationHistory
    ) {
        return generateTurn(userText, user, documentContext, conversationHistory, null);
    }

    public AiReply generateTurn(
            String userText,
            User user,
            String documentContext,
            List<ChatMessage> conversationHistory,
            Map<String, String> learningType
    ) {
        if (shouldAskForClarification(userText)) {
            return new AiReply(
                    "입력이 너무 짧아서 의미를 정확히 모르겠어요. 한글/영어 입력 상태를 확인해서 다시 입력해 주세요.",
                    "question"
            );
        }

        ReplyMode mode = classifyReplyMode(userText);
        String prompt = buildTurnPrompt(userText, user, documentContext, conversationHistory, learningType, mode);
        String content = requestText(prompt);
        return new AiReply(content, mode == ReplyMode.DIRECT_ANSWER ? "answer" : "question");
    }

    public String generateHint(
            User user,
            Conversation conversation,
            int hintNumber,
            List<ChatMessage> conversationHistory,
            List<ChatMessage> previousHints
    ) {
        return requestText(buildHintPrompt(user, conversation, hintNumber, conversationHistory, previousHints));
    }

    public String generateCompletionFeedback(User user, Conversation conversation) {
        return generateCompletionFeedback(user, conversation, List.of());
    }

    public String generateCompletionFeedback(
            User user,
            Conversation conversation,
            List<ChatMessage> conversationHistory
    ) {
        String prompt = """
                너는 Ducky, 러버덕 디버깅을 돕는 학습 코치야.
                학습 세션을 마무리하는 짧은 피드백을 한국어로 작성해.
                현재 세션 전체 대화 기록을 근거로 사용자가 스스로 설명한 점을 격려하고,
                다음 세션에서 이어갈 복습 방향을 한 문장으로 제안해.

                사용자 이름: %s
                세션 제목: %s
                주제: %s
                현재 세션 전체 대화 기록:
                %s
                """.formatted(
                user.getName(),
                conversation.getTitle(),
                conversation.getTopic(),
                formatConversationHistory(conversationHistory)
        );

        return requestText(prompt);
    }

    String buildTurnPrompt(
            String userText,
            User user,
            String documentContext,
            List<ChatMessage> conversationHistory,
            Map<String, String> learningType
    ) {
        return buildTurnPrompt(
                userText,
                user,
                documentContext,
                conversationHistory,
                learningType,
                classifyReplyMode(userText)
        );
    }

    String buildHintPrompt(
            User user,
            Conversation conversation,
            int hintNumber,
            List<ChatMessage> conversationHistory,
            List<ChatMessage> previousHints
    ) {
        LearningStyleContext style = resolveLearningStyle(user, null);
        return """
                너는 Ducky야. 사용자가 현재 대화에서 막힌 지점을 스스로 넘도록 돕는 러버덕 학습 파트너야.
                한국어로 자연스럽고 친근하게 답해.

                힌트 작성 규칙:
                - 출력은 힌트 본문만 작성한다. "힌트 %d", "힌트 1:" 같은 번호, 제목, 머리말을 붙이지 마.
                - 현재 세션 전체 대화 기록에서 사용자가 마지막으로 막힌 지점을 찾아 그 맥락에 직접 붙는 단서만 준다.
                - 이미 제공한 힌트와 같은 일반론을 반복하지 않는다.
                - 설명형 튜터처럼 답하지 마. 정의·결론·해결책·원인 단정은 금지.
                - 나쁜 예: "range(n)의 경우 0부터 n-1까지의 숫자가 반환됩니다" (개념 강의 ❌)
                - 나쁜 예: "이 부분에서 오류가 발생했을 수 있습니다" (원인 추측·단정 ❌)
                - 좋은 예: "range(n)을 실행했을 때 실제로 어떤 값들이 나왔는지 확인해봤어?" (관찰 유도 ⭕)
                - 사용자가 스스로 확인·깨달음에 이르게 하는 질문 1개로 끝낸다.
                - 1~2문장으로 끝낸다.

                세션 제목: %s
                주제: %s

                사용자 학습 유형:
                %s

                학습유형별 힌트 지침:
                %s

                현재 세션 전체 대화 기록:
                %s

                이미 제공한 힌트:
                %s
                """.formatted(
                hintNumber,
                conversation.getTitle(),
                conversation.getTopic(),
                formatStyleSummary(style),
                buildLearningStyleInstructions(style),
                formatConversationHistory(conversationHistory),
                formatPreviousHints(previousHints)
        );
    }

    private String buildTurnPrompt(
            String userText,
            User user,
            String documentContext,
            List<ChatMessage> conversationHistory,
            Map<String, String> learningType,
            ReplyMode mode
    ) {
        LearningStyleContext style = resolveLearningStyle(user, learningType);
        String modeInstruction = mode == ReplyMode.DIRECT_ANSWER
                ? """
                이번 입력은 인사, 자기소개, 사용법, 명령어, 운영 절차, 앱/캐릭터 정체성, 간단한 확인 질문 같은 실용 대화로 처리해.
                먼저 직접 답해. 필요한 명령어나 순서를 바로 알려주고, 대화 맥락상 이미 분명한 대상을 다시 묻지 마.
                필요하면 마지막에 아주 짧게 다음 확인 단계만 붙여.
                """
                : """
                이번 입력은 학습/디버깅/개념 이해를 돕는 러버덕 모드로 처리해.
                설명형 튜터가 아니라, 듣고 질문하는 러버덕이다.

                응답 순서:
                1. 현재 세션 대화·입력·문서 맥락을 읽고, 사용자가 어디까지 이해했는지·어디서 막혔는지 먼저 파악한다.
                2. 그 지점에 맞는 질문 1개만 출력한다. 질문으로 끝낸다.

                절대 하지 말 것:
                - 개념·문법·API 정의를 설명하지 마.
                  나쁜 예: "range(n)의 경우 0부터 n-1까지의 숫자가 반환됩니다" ❌
                - 정답, 결론, 해결책, 수정 방법을 알려주지 마.
                - 오류 원인·버그 위치·실수 지점을 단정하거나 추측하지 마.
                  나쁜 예: "이 부분에서 오류가 발생했을 수 있습니다" ❌
                - 사실을 전달하는 평서문(설명·강의)으로 끝내지 마.
                - "해당 문구에 대해 설명해달라"처럼 입력 자체를 반복하는 응답은 하지 마.

                해야 할 것:
                - 사용자가 이미 말한 키워드·코드·표현만 인용해 되짚을 수 있다. 새 지식·정의·원인은 넣지 마.
                - 사용자가 직접 확인·설명·깨달음에 이르게 하는 구체적인 질문 1개.
                """;

        return """
                너는 Ducky야. 설명형 튜터가 아니라, 사용자가 스스로 깨달을 때까지 듣고 질문하는 러버덕 학습 파트너야.
                한국어로 자연스럽고 친근하게 답해.

                공통 규칙:
                - 사용자의 말이 "넌 누구니?", "뭐야?", "어떻게 써?" 같은 메타 질문이면 Ducky가 누구인지 직접 답한다.
                - 명령어, 서버 운영, 도구 사용법, 재시작/설치/실행 방법 질문은 실행 가능한 답을 먼저 준다.
                - 실제 오류, 코드, 개념, 과제, 문서 내용 질문이면 러버덕 디버깅 방식으로 돕는다.
                - 러버덕 방식: 설명 ❌, 결론 ❌, 원인 단정 ❌, 질문 ⭕, 사용자 깨달음 유도 ⭕
                - 러버덕 모드에서는 먼저 맥락을 이해한 뒤, 그 지점에 맞는 질문 1개만 던진다.
                - 한 응답에 질문은 1개만 넣고, 러버덕 모드 응답은 질문으로 끝낸다.
                - 사용자가 올린 문서 맥락은 관련 있을 때만 사용한다. 문서 내용을 그대로 강의하지 마.
                - "이름", "그거", "다른 것", "이건" 같은 짧은 후속 질문은 현재 세션 전체 대화 기록에서 지시 대상을 찾되, 정답·원인 대신 그 대상에 대해 사용자가 스스로 말하게 유도하는 질문을 던진다.
                - 현재 세션 밖의 다른 대화 기록을 안다고 말하지 않는다.

                %s

                사용자 학습 유형:
                %s

                학습유형별 응답 지침:
                %s

                현재 세션 전체 대화 기록:
                %s

                참고 문서 맥락:
                %s

                현재 사용자 입력:
                %s
                """.formatted(
                modeInstruction,
                formatStyleSummary(style),
                buildLearningStyleInstructions(style),
                formatConversationHistory(conversationHistory),
                blankToNone(documentContext),
                blankToNone(userText)
        );
    }

    private ReplyMode classifyReplyMode(String userText) {
        String normalized = userText == null ? "" : userText.replaceAll("\\s+", "").toLowerCase();

        if (normalized.isBlank()) {
            return ReplyMode.DIRECT_ANSWER;
        }

        if (normalized.equals("hi") || normalized.equals("hello")) {
            return ReplyMode.DIRECT_ANSWER;
        }

        if (containsAny(
                normalized,
                "넌누구",
                "너는누구",
                "누구야",
                "정체",
                "ducky가뭐",
                "덕키가뭐",
                "뭐하는",
                "어떻게써",
                "사용법",
                "안녕",
                "하이"
        )) {
            return ReplyMode.DIRECT_ANSWER;
        }

        if (containsAny(
                normalized,
                "pm2",
                "npm",
                "gradle",
                "git",
                "docker",
                "서버에서",
                "명령어",
                "재시작",
                "실행",
                "설치",
                "배포",
                "사용"
        )) {
            return ReplyMode.DIRECT_ANSWER;
        }

        return ReplyMode.RUBBER_DUCK;
    }

    private boolean shouldAskForClarification(String userText) {
        String normalized = userText == null ? "" : userText.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.equals("hi") || normalized.equals("hello")) {
            return false;
        }

        return normalized.length() <= 2 && normalized.matches("[a-z0-9]+");
    }

    LearningStyleContext resolveLearningStyle(User user, Map<String, String> learningType) {
        String processing = user == null ? "active" : user.getProcessingStyle();
        String expression = user == null ? "visual" : user.getExpressionStyle();
        String understanding = user == null ? "sequential" : user.getUnderstandingStyle();

        if (learningType != null) {
            processing = valueOrFallback(learningType.get("processing"), processing);
            expression = valueOrFallback(learningType.get("expression"), expression);
            understanding = valueOrFallback(
                    valueOrFallback(learningType.get("understanding"), learningType.get("structure")),
                    understanding
            );
        }

        return new LearningStyleContext(
                normalizeLearningStyle(processing, "active"),
                normalizeLearningStyle(expression, "visual"),
                normalizeLearningStyle(understanding, "sequential")
        );
    }

    String formatConversationHistory(List<ChatMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "없음";
        }

        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : conversationHistory) {
            if (message.getMessageText() == null || message.getMessageText().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- [")
                    .append(message.getSequenceNo())
                    .append("] ")
                    .append(senderLabel(message));
            if (message.getHintNumber() != null) {
                builder.append(" ").append(message.getHintNumber()).append("번째 힌트");
            }
            builder.append(" (")
                    .append(blankToNone(message.getMessageType()))
                    .append("): ")
                    .append(message.getMessageText().trim());
        }

        return builder.isEmpty() ? "없음" : builder.toString();
    }

    private String formatPreviousHints(List<ChatMessage> previousHints) {
        if (previousHints == null || previousHints.isEmpty()) {
            return "없음";
        }

        StringBuilder builder = new StringBuilder();
        int fallbackNumber = 1;
        for (ChatMessage hint : previousHints) {
            if (hint.getMessageText() == null || hint.getMessageText().isBlank()) {
                continue;
            }
            int hintNumber = hint.getHintNumber() == null ? fallbackNumber : hint.getHintNumber();
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- ")
                    .append(hintNumber)
                    .append("번째 제공 내용: ")
                    .append(hint.getMessageText().trim());
            fallbackNumber++;
        }

        return builder.isEmpty() ? "없음" : builder.toString();
    }

    private String formatStyleSummary(LearningStyleContext style) {
        return """
                - 처리 방식: %s
                - 표현 선호: %s
                - 이해 구조: %s
                """.formatted(style.processing(), style.expression(), style.understanding()).trim();
    }

    private String buildLearningStyleInstructions(LearningStyleContext style) {
        return """
                - 처리 방식(%s): %s
                - 표현 선호(%s): %s
                - 이해 구조(%s): %s
                """.formatted(
                style.processing(),
                processingInstruction(style.processing()),
                style.expression(),
                expressionInstruction(style.expression()),
                style.understanding(),
                understandingInstruction(style.understanding())
        ).trim();
    }

    private String processingInstruction(String processing) {
        return switch (processing) {
            case "reflective" -> "조건·가정을 사용자에게 직접 말하게 유도하는 질문을 던진다. 원인을 대신 짚거나 단정하지 않는다.";
            case "active" -> "바로 확인할 작은 실험·관찰을 질문 형태로 제안한다. 무엇이 틀렸는지 말해주지 않는다.";
            default -> "작게 시도할 점과 생각을 정리할 점을 사용자가 말하게 유도하는 질문을 던진다.";
        };
    }

    private String expressionInstruction(String expression) {
        return switch (expression) {
            case "verbal" -> "자신의 말로 풀어 설명하게 만드는 질문을 던진다. 개념 정의나 핵심 문장을 대신 써주지 않는다.";
            case "visual" -> "구조·흐름·비교를 스스로 그리거나 묘사하게 유도하는 질문을 던진다. 답을 그려주지 않는다.";
            default -> "구조와 말로 정리하게 사용자가 직접 말하도록 질문으로 이끈다. 내용을 대신 정리해주지 않는다.";
        };
    }

    private String understandingInstruction(String understanding) {
        return switch (understanding) {
            case "global" -> "전체 그림부터 스스로 잡게 한 뒤 세부로 내려가게 질문으로 이끈다. 전체 그림을 대신 설명하지 않는다.";
            case "sequential" -> "순서대로 한 단계씩 스스로 설명하게 이어지는 질문을 던진다. 단계 내용을 대신 설명하지 않는다.";
            default -> "전체 맥락과 다음 단계를 사용자가 직접 말하게 연결하는 질문을 던진다.";
        };
    }

    private String senderLabel(ChatMessage message) {
        if ("user".equals(message.getSender())) {
            return "사용자";
        }
        if ("system".equals(message.getSender())) {
            return "시스템";
        }
        if ("hint".equals(message.getMessageType())) {
            return "Ducky";
        }
        return "Ducky";
    }

    private String normalizeLearningStyle(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String requestText(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CustomException(ErrorCode.CHAT_RESPONSE_UNAVAILABLE);
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(Map.of(
                            "model", model,
                            "input", List.of(Map.of(
                                    "role", "user",
                                    "content", prompt
                            ))
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            String outputText = extractOutputText(response);
            if (outputText.isBlank()) {
                throw new CustomException(ErrorCode.CHAT_RESPONSE_UNAVAILABLE);
            }

            return outputText;
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new CustomException(ErrorCode.CHAT_RESPONSE_UNAVAILABLE);
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return "";
        }

        String directText = response.path("output_text").asText("");
        if (!directText.isBlank()) {
            return directText.trim();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    String text = contentItem.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!builder.isEmpty()) {
                            builder.append("\n");
                        }
                        builder.append(text.trim());
                    }
                }
            }
        }

        return builder.toString().trim();
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }
}
