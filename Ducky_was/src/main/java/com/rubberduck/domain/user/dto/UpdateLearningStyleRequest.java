package com.rubberduck.domain.user.dto;

public record UpdateLearningStyleRequest(
        String processing,
        String expression,
        String understanding,
        Boolean onboarded
) {
}
