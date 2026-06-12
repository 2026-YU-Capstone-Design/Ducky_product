package com.rubberduck.domain.user.dto;

import com.rubberduck.domain.user.entity.User;

public record UserResponse(
        String id,
        String name,
        String email,
        String loginId,
        String level,
        LearningStyleResponse learningStyle,
        boolean onboarded,
        int streakDays,
        int completedSessionCount
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getName(),
                user.getEmail(),
                user.getLoginId(),
                user.getLevel(),
                new LearningStyleResponse(
                        user.getProcessingStyle(),
                        user.getExpressionStyle(),
                        user.getUnderstandingStyle()
                ),
                user.isOnboarded(),
                user.getStreakDays(),
                user.getCompletedSessionCount()
        );
    }

    public record LearningStyleResponse(
            String processing,
            String expression,
            String understanding
    ) {
    }
}
