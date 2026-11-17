package com.rubberduck.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record KakaoLoginRequest(
        String code,
        @JsonAlias("redirect_uri") String redirectUri
) {
}
