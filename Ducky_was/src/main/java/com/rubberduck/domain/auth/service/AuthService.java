package com.rubberduck.domain.auth.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rubberduck.domain.auth.dto.AuthResponse;
import com.rubberduck.domain.auth.dto.KakaoLoginRequest;
import com.rubberduck.domain.auth.dto.LoginRequest;
import com.rubberduck.domain.auth.dto.NaverLoginRequest;
import com.rubberduck.domain.auth.dto.SignupRequest;
import com.rubberduck.domain.auth.entity.SocialAccount;
import com.rubberduck.domain.auth.repository.SocialAccountRepository;
import com.rubberduck.domain.auth.service.KakaoOAuthClient.KakaoUser;
import com.rubberduck.domain.auth.service.NaverOAuthClient.NaverUser;
import com.rubberduck.domain.user.dto.UserResponse;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.domain.user.repository.UserRepository;
import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String KAKAO_PROVIDER = "kakao";
    private static final String NAVER_PROVIDER = "naver";
    private static final String SOCIAL_EMAIL_DOMAIN = "social.ducky.local";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(normalizeEmail(email));
    }

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

    @Transactional
    public AuthResponse kakaoLogin(KakaoLoginRequest request) {
        String code = required(request.code());
        String redirectUri = required(request.redirectUri());
        KakaoUser kakaoUser = kakaoOAuthClient.fetchUser(code, redirectUri);
        String providerUserId = required(kakaoUser.providerUserId());

        return socialAccountRepository.findByProviderAndProviderUserId(KAKAO_PROVIDER, providerUserId)
                .map(SocialAccount::getUser)
                .map(user -> syncSocialProfile(user, kakaoUser.nickname(), "Kakao User"))
                .map(this::toAuthResponse)
                .orElseGet(() -> createSocialAuthResponse(
                        KAKAO_PROVIDER,
                        providerUserId,
                        kakaoUser.email(),
                        kakaoUser.nickname(),
                        "Kakao User"
                ));
    }

    @Transactional
    public AuthResponse naverLogin(NaverLoginRequest request) {
        String code = required(request.code());
        String state = required(request.state());
        String redirectUri = required(request.redirectUri());
        NaverUser naverUser = naverOAuthClient.fetchUser(code, state, redirectUri);
        String providerUserId = required(naverUser.providerUserId());

        return socialAccountRepository.findByProviderAndProviderUserId(NAVER_PROVIDER, providerUserId)
                .map(SocialAccount::getUser)
                .map(user -> syncSocialProfile(user, naverUser.nickname(), "Naver User"))
                .map(this::toAuthResponse)
                .orElseGet(() -> createSocialAuthResponse(
                        NAVER_PROVIDER,
                        providerUserId,
                        naverUser.email(),
                        naverUser.nickname(),
                        "Naver User"
                ));
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

    private AuthResponse createSocialAuthResponse(
            String provider,
            String providerUserId,
            String providerEmail,
            String nickname,
            String fallbackName
    ) {
        String email = normalizeSocialEmail(provider, providerEmail, providerUserId);
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.create(
                        normalizeName(nickname, fallbackName),
                        email,
                        uniqueLoginId(provider + "_" + normalizeSocialId(providerUserId)),
                        passwordEncoder.encode(UUID.randomUUID().toString())
                )));
        socialAccountRepository.save(SocialAccount.create(user, provider, providerUserId, email));
        return toAuthResponse(user);
    }

    private User syncSocialProfile(User user, String nickname, String fallbackName) {
        if (hasSocialFallbackName(user, fallbackName) && nickname != null && !nickname.isBlank()) {
            user.setName(nickname.trim());
        }
        return user;
    }

    private boolean hasSocialFallbackName(User user, String fallbackName) {
        String name = user.getName();
        return name == null || name.isBlank() || fallbackName.equals(name);
    }

    private String normalizeSocialEmail(String provider, String email, String providerUserId) {
        if (email != null && !email.isBlank() && email.contains("@")) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        return provider + "_" + normalizeSocialId(providerUserId) + "@" + SOCIAL_EMAIL_DOMAIN;
    }

    private String normalizeSocialId(String providerUserId) {
        String normalized = required(providerUserId).replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private String uniqueLoginId(String baseLoginId) {
        String base = trimToLength(baseLoginId, 80);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByLoginId(candidate)) {
            String suffixText = "_" + suffix;
            candidate = trimToLength(base, 80 - suffixText.length()) + suffixText;
            suffix++;
        }
        return candidate;
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

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value.trim();
    }
}
