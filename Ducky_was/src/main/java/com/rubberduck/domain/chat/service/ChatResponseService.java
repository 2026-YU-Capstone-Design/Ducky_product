package com.rubberduck.domain.chat.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.rubberduck.domain.chat.entity.Conversation;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

@Service
public class ChatResponseService {

    public record AiReply(String content, String type) {
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

    public AiReply generateTurn(String userText, User user, String documentContext) {
        ReplyMode mode = classifyReplyMode(userText);
        String prompt = buildTurnPrompt(userText, user, documentContext, mode);
        String content = requestText(prompt);
        return new AiReply(content, mode == ReplyMode.DIRECT_ANSWER ? "answer" : "question");
    }

    public String generateHint(Conversation conversation, int hintNumber) {
        String prompt = """
                너는 Ducky, 러버덕 디버깅을 돕는 학습 코치야.
                힌트 %d번을 작성해.
                정답을 바로 공개하지 말고, 사용자가 다음 사고 단계를 밟도록 한두 문장으로 안내해.
                사용자가 바로 확인할 수 있는 관찰 포인트나 작은 실험을 제안해.

                세션 제목: %s
                주제: %s
                """.formatted(hintNumber, conversation.getTitle(), conversation.getTopic());

        return requestText(prompt);
    }

    public String generateCompletionFeedback(User user, Conversation conversation) {
        String prompt = """
                너는 Ducky, 러버덕 디버깅을 돕는 학습 코치야.
                학습 세션을 마무리하는 짧은 피드백을 한국어로 작성해.
                사용자가 스스로 설명한 점을 격려하고, 다음 세션에서 이어갈 복습 방향을 한 문장으로 제안해.

                사용자 이름: %s
                세션 제목: %s
                주제: %s
                """.formatted(user.getName(), conversation.getTitle(), conversation.getTopic());

        return requestText(prompt);
    }

    private String buildTurnPrompt(
            String userText,
            User user,
            String documentContext,
            ReplyMode mode
    ) {
        String modeInstruction = mode == ReplyMode.DIRECT_ANSWER
                ? """
                이번 입력은 인사, 자기소개, 사용법, 앱/캐릭터 정체성, 간단한 확인 질문 같은 일반 대화로 처리해.
                먼저 직접 답해. 억지로 "그 문구를 설명해달라"거나 문제 상황을 말하라고 되묻지 마.
                필요하면 마지막에 아주 짧게 "궁금한 문제를 말해주면 같이 정리해볼게" 정도로만 이어가.
                """
                : """
                이번 입력은 학습/디버깅/개념 이해를 돕는 러버덕 모드로 처리해.
                그래도 무조건 질문만 던지지 마. 사용자가 이미 묻는 개념에는 짧게 설명한 뒤,
                사용자가 스스로 생각을 이어갈 수 있는 구체적인 다음 질문 1개를 덧붙여.
                "해당 문구에 대해 설명해달라"처럼 입력 자체를 반복하는 응답은 하지 마.
                """;

        return """
                너는 Ducky야. 사용자가 문제를 말로 풀어 설명하도록 돕는 러버덕 학습 파트너야.
                한국어로 자연스럽고 친근하게 답해.

                공통 규칙:
                - 사용자의 말이 "넌 누구니?", "뭐야?", "어떻게 써?" 같은 메타 질문이면 Ducky가 누구인지 직접 답한다.
                - 실제 오류, 코드, 개념, 과제, 문서 내용 질문이면 러버덕 디버깅 방식으로 돕는다.
                - 러버덕 방식은 정답 회피가 아니라, 짧은 설명 + 스스로 점검할 질문/단계 제안이다.
                - 한 응답에 질문은 최대 1개만 넣는다.
                - 물음표만 반복하거나 모든 문장을 질문으로 끝내지 않는다.
                - 사용자가 올린 문서 맥락은 관련 있을 때만 사용한다.

                %s

                사용자 학습 유형:
                - 처리 방식: %s
                - 표현 선호: %s
                - 이해 구조: %s

                참고 문서 맥락:
                %s

                사용자 입력:
                %s
                """.formatted(
                modeInstruction,
                user.getProcessingStyle(),
                user.getExpressionStyle(),
                user.getUnderstandingStyle(),
                blankToNone(documentContext),
                userText
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

        return ReplyMode.RUBBER_DUCK;
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
