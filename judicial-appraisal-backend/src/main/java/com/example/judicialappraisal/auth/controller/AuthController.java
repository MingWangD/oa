package com.example.judicialappraisal.auth.controller;

import com.example.judicialappraisal.auth.dto.ChangePasswordRequest;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.LoginRequest;
import com.example.judicialappraisal.auth.dto.LoginResponse;
import com.example.judicialappraisal.auth.service.AuthService;
import com.example.judicialappraisal.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody com.example.judicialappraisal.auth.dto.RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserInfo> me(Authentication authentication) {
        return ApiResponse.success(authService.getCurrentUser(currentUserId(authentication)));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        authService.changePassword(currentUserId(authentication), request);
        return ApiResponse.success();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return userInfo.id();
    }
}
