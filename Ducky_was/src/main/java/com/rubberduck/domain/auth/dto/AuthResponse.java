package com.rubberduck.domain.auth.dto;

import com.rubberduck.domain.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        UserResponse userInfo
) {
}
