package com.rubberduck.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LoginRequest(
        String email,
        @JsonAlias("login_id") String loginId,
        String password
) {
}
