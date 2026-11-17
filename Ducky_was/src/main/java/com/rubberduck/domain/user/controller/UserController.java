package com.rubberduck.domain.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.user.dto.UpdateLearningStyleRequest;
import com.rubberduck.domain.user.dto.UserResponse;
import com.rubberduck.domain.user.service.UserService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.ok(UserResponse.from(user));
    }

    @PatchMapping("/me/learning-style")
    public ApiResponse<UserResponse> updateLearningStyle(
            @AuthenticationPrincipal User user,
            @RequestBody UpdateLearningStyleRequest request
    ) {
        return ApiResponse.ok(UserResponse.from(
                userService.updateLearningStyle(user, request)
        ));
    }
}
