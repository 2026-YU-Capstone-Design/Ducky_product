package com.rubberduck.domain.auth.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.auth.dto.AuthResponse;
import com.rubberduck.domain.auth.dto.LoginRequest;
import com.rubberduck.domain.auth.dto.SignupRequest;
import com.rubberduck.domain.user.dto.UserResponse;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String loginId = normalizeLoginId(request.loginId(), email);

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByLoginId(loginId)) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.create(
                normalizeName(request.name(), loginId),
                email,
                loginId,
                passwordEncoder.encode(required(request.password()))
        );
        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.email() != null && !request.email().isBlank()
                ? request.email().trim().toLowerCase(Locale.ROOT)
                : required(request.loginId());

        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByLoginId(identifier))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(required(request.password()), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public User requireUser(String authorizationHeader) {
        Long userId = authTokenService.parseUserId(authorizationHeader)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(authTokenService.issue(user.getId()), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return required(email).toLowerCase(Locale.ROOT);
    }

    private String normalizeLoginId(String loginId, String email) {
        if (loginId != null && !loginId.isBlank()) {
            return loginId.trim();
        }
        return email.split("@")[0];
    }

    private String normalizeName(String name, String fallback) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return fallback;
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value.trim();
    }
}
