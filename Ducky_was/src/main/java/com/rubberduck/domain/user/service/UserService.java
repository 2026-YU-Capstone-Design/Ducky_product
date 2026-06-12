package com.rubberduck.domain.user.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User getByLoginIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        return userRepository.findByEmail(normalized.toLowerCase(Locale.ROOT))
                .or(() -> userRepository.findByLoginId(normalized))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User resolveUserReference(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        String normalized = userId.trim();
        if (normalized.matches("\\d+")) {
            return getById(Long.parseLong(normalized));
        }
        return getByLoginIdentifier(normalized);
    }

    @Transactional
    public User findOrCreateExternalUser(String userId) {
        String normalized = normalizeExternalUserId(userId);
        return userRepository.findByLoginId(normalized)
                .orElseGet(() -> userRepository.save(User.create(
                        normalized,
                        normalized + "@ducky.local",
                        normalized,
                        "{external-device-user}"
                )));
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return identifier.trim();
    }

    private String normalizeExternalUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "test-user";
        }
        return userId.trim();
    }
}
