package com.rubberduck.domain.chat.service;

import org.springframework.stereotype.Service;

import com.rubberduck.domain.user.entity.User;

@Service
public class FallbackChatResponseService {

    public String generateQuestion(String userText, User user) {
        return generateQuestion(userText, user, "");
    }

    public String generateQuestion(String userText, User user, String documentContext) {
        String focus = summarize(userText, 40);
        String styleGuide = "sequential".equals(user.getUnderstandingStyle())
                ? "단계별로"
                : "전체 흐름부터";
        String contextGuide = documentContext == null || documentContext.isBlank()
                ? ""
                : " 업로드한 자료에서는 \"" + summarize(documentContext, 90) + "\" 부분도 함께 보여요.";

        return "좋아요. \"" + focus + "\" 부분을 " + styleGuide
                + " 설명해볼까요?" + contextGuide
                + " 먼저 어떤 입력이나 상황에서 막혔는지 한 문장으로 정리해보세요.";
    }

    public String generateHint(int hintNumber) {
        int level = Math.min(((hintNumber - 1) / 2) + 1, 3);
        if (level == 1) {
            return "힌트 " + hintNumber + ": 지금 알고 있는 조건과 기대한 결과를 나란히 적어보세요.";
        }
        if (level == 2) {
            return "힌트 " + hintNumber + ": 문제 흐름을 작은 단계로 나누고, 각 단계에서 값이 어떻게 바뀌는지 확인해보세요.";
        }
        return "힌트 " + hintNumber + ": 가장 작은 예시를 만든 뒤 기대값과 실제값이 처음 달라지는 지점을 찾아보세요.";
    }

    public String generateCompletionFeedback() {
        return "좋아요. 지금처럼 스스로 설명할 수 있으면 이번 세션은 완료해도 됩니다. 다음에는 같은 기준으로 새 문제를 다시 쪼개보세요.";
    }

    private String summarize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "방금 말한 문제";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
