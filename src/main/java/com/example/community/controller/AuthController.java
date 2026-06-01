package com.example.community.controller;

import com.example.community.domain.user.AuthService;
import com.example.community.domain.user.dto.LoginRequestDto;
import com.example.community.domain.user.dto.LoginResponseDto;
import com.example.community.domain.user.dto.RefreshRequestDto;
import com.example.community.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@RequestBody @Valid LoginRequestDto request) {

        LoginResponseDto response = authService.login(
                request.getUsername(),
                request.getPassword()
        );

        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponseDto> refresh(@RequestBody @Valid RefreshRequestDto request) {

        LoginResponseDto response = authService.reissueToken(request.getRefreshToken());

        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {

        String username = authentication.getName();
        authService.logout(username);

        return ApiResponse.success(null);
    }
}