package com.rubberduck.domain.auth.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class KakaoOAuthClient {

    public record KakaoUser(String providerUserId, String email, String nickname) {
    }

    private final WebClient authClient;
    private final WebClient apiClient;
    private final String restApiKey;
    private final String clientSecret;
    private final Duration timeout;

    public KakaoOAuthClient(
            @Value("${oauth.kakao.rest-api-key:}") String restApiKey,
            @Value("${oauth.kakao.client-secret:}") String clientSecret,
            @Value("${oauth.kakao.request-timeout-seconds:10}") long timeoutSeconds
    ) {
        this.authClient = WebClient.builder().baseUrl("https://kauth.kakao.com").build();
        this.apiClient = WebClient.builder().baseUrl("https://kapi.kakao.com").build();
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public KakaoUser fetchUser(String code, String redirectUri) {
        String accessToken = requestAccessToken(code, redirectUri);
        JsonNode response = requestUserInfo(accessToken);
        String providerUserId = response.path("id").asText("");
        if (providerUserId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        JsonNode account = response.path("kakao_account");
        return new KakaoUser(
                providerUserId,
                account.path("email").asText(""),
                account.path("profile").path("nickname").asText("")
        );
    }

    private String requestAccessToken(String code, String redirectUri) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            JsonNode response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);
            String accessToken = response == null ? "" : response.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            return accessToken;
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private JsonNode requestUserInfo(String accessToken) {
        try {
            JsonNode response = apiClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);
            if (response == null || response.isNull()) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            return response;
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
