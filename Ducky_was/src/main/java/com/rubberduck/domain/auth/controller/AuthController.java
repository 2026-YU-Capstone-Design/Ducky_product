package com.rubberduck.domain.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rubberduck.domain.auth.dto.AuthResponse;
import com.rubberduck.domain.auth.dto.EmailAvailabilityResponse;
import com.rubberduck.domain.auth.dto.KakaoLoginRequest;
import com.rubberduck.domain.auth.dto.LoginRequest;
import com.rubberduck.domain.auth.dto.SignupRequest;
import com.rubberduck.domain.auth.service.AuthService;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/oauth/kakao")
    public ApiResponse<AuthResponse> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(authService.kakaoLogin(request));
    }

    @GetMapping("/email-available")
    public ApiResponse<EmailAvailabilityResponse> checkEmailAvailability(@RequestParam String email) {
        return ApiResponse.ok(new EmailAvailabilityResponse(authService.isEmailAvailable(email)));
    }
}
