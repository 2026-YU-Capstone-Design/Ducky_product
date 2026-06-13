package com.rubberduck.domain.auth.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class NaverOAuthClient {

    public record NaverUser(String providerUserId, String email, String nickname) {
    }

    private final WebClient authClient;
    private final WebClient apiClient;
    private final String clientId;
    private final String clientSecret;
    private final Duration timeout;

    public NaverOAuthClient(
            @Value("${oauth.naver.client-id:}") String clientId,
            @Value("${oauth.naver.client-secret:}") String clientSecret,
            @Value("${oauth.naver.request-timeout-seconds:10}") long timeoutSeconds
    ) {
        this.authClient = WebClient.builder().baseUrl("https://nid.naver.com").build();
        this.apiClient = WebClient.builder().baseUrl("https://openapi.naver.com").build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public NaverUser fetchUser(String code, String state, String redirectUri) {
        String accessToken = requestAccessToken(code, state, redirectUri);
        JsonNode profile = requestUserInfo(accessToken).path("response");
        String providerUserId = profile.path("id").asText("");
        if (providerUserId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        return new NaverUser(
                providerUserId,
                profile.path("email").asText(""),
                profile.path("nickname").asText("")
        );
    }

    private String requestAccessToken(String code, String state, String redirectUri) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        try {
            JsonNode response = authClient.get()
                    .uri(uriBuilder -> tokenUri(uriBuilder, code, state, redirectUri))
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

    private java.net.URI tokenUri(UriBuilder uriBuilder, String code, String state, String redirectUri) {
        return uriBuilder
                .path("/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .queryParam("state", state)
                .build();
    }

    private JsonNode requestUserInfo(String accessToken) {
        try {
            JsonNode response = apiClient.get()
                    .uri("/v1/nid/me")
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
