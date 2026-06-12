package com.rubberduck.domain.chat.service;

import org.springframework.stereotype.Service;

import com.rubberduck.domain.user.entity.User;

@Service
public class FallbackChatResponseService {

    public String generateQuestion(String userText, User user) {
        String focus = summarize(userText);
        String styleGuide = "sequential".equals(user.getUnderstandingStyle())
                ? "한 단계씩"
                : "전체 흐름부터";

        return "좋아요. \"" + focus + "\" 부분을 " + styleGuide
                + " 설명해볼까요? 먼저 어떤 입력이나 상황에서 막혔는지 한 문장으로 정리해보세요.";
    }

    public String generateHint(int hintNumber) {
        int level = Math.min(((hintNumber - 1) / 2) + 1, 3);
        if (level == 1) {
            return "힌트 " + hintNumber + ": 지금 알고 있는 조건과 기대한 결과를 나란히 적어보세요.";
        }
        if (level == 2) {
            return "힌트 " + hintNumber + ": 문제 흐름을 작은 단계로 나누고, 각 단계에서 실제 값이 어떻게 바뀌는지 확인해보세요.";
        }
        return "힌트 " + hintNumber + ": 가장 작은 재현 예시를 만든 뒤, 기대값과 실제값이 처음 달라지는 지점을 찾아보세요.";
    }

    public String generateCompletionFeedback() {
        return "좋아요. 지금처럼 스스로 설명할 수 있으면 이 세션은 완료해도 됩니다. 다음에는 같은 기준으로 새 문제를 다시 쪼개보세요.";
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "방금 말한 문제";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 40) {
            return normalized;
        }
        return normalized.substring(0, 40) + "...";
    }
}
