package com.rubberduck.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SignupRequest(
        String name,
        String email,
        @JsonAlias("login_id") String loginId,
        String password
) {
}
