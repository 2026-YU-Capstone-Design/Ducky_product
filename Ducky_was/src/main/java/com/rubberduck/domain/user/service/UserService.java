package com.rubberduck.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.user.dto.UpdateLearningStyleRequest;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User updateLearningStyle(User user, UpdateLearningStyleRequest request) {
        user.setProcessingStyle(normalizeLearningValue(request.processing(), "active"));
        user.setExpressionStyle(normalizeLearningValue(request.expression(), "visual"));
        user.setUnderstandingStyle(normalizeLearningValue(request.understanding(), "sequential"));
        if (request.onboarded() != null) {
            user.setOnboarded(request.onboarded());
        }
        return userRepository.save(user);
    }

    private String normalizeLearningValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
