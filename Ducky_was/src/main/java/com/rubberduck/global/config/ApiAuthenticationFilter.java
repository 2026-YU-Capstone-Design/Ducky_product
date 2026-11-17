package com.rubberduck.global.config;

import java.io.IOException;
import java.util.List;

import com.rubberduck.domain.auth.service.AuthTokenService;
import com.rubberduck.domain.device.repository.DeviceUserRepository;
import com.rubberduck.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ApiAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEVICE_PREFIX = "Bearer device.";

    private final AuthTokenService tokens;
    private final UserRepository users;
    private final DeviceUserRepository devices;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, DEVICE_PREFIX, 0, DEVICE_PREFIX.length())) {
            String key = header.substring(DEVICE_PREFIX.length());
            if (key.matches("[A-Za-z0-9_-]{43}")) {
                devices.findByTokenHash(AuthTokenService.hashDeviceKey(key))
                        .ifPresent(link -> authenticate(link, "DEVICE"));
            }
        } else {
            tokens.parseUserId(header).flatMap(users::findById)
                    .ifPresent(user -> authenticate(user, "USER"));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Object principal, String authority) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
