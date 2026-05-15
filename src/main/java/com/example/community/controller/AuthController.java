package com.example.community.controller;

import com.example.community.domain.user.AuthService;
import com.example.community.domain.user.dto.LoginRequestDto;
import com.example.community.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody @Valid LoginRequestDto request) {

        String token = authService.login(
                request.getUsername(),
                request.getPassword()
        );

        return ApiResponse.success(token);
    }
}