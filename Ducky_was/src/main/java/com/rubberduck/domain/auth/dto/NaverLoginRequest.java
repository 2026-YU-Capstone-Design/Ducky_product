package com.rubberduck.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record NaverLoginRequest(
        String code,
        String state,
        @JsonAlias("redirect_uri") String redirectUri
) {
}
