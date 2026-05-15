package com.example.community.controller;

import com.example.community.domain.user.User;
import com.example.community.domain.user.UserService;
import com.example.community.domain.user.dto.RegisterRequestDto;
import com.example.community.domain.user.dto.UserResponseDto;
import com.example.community.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public ApiResponse<UserResponseDto> register(@RequestBody @Valid RegisterRequestDto request) {
        User user = userService.register(request.getUsername(), request.getEmail(), request.getPassword());
        return ApiResponse.success(new UserResponseDto(user));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponseDto> me(Authentication authentication){
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        return ApiResponse.success(new UserResponseDto(user));
    }


}