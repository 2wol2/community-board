package com.example.community.controller;

import com.example.community.domain.user.User;
import com.example.community.domain.user.UserRepository;
import com.example.community.domain.user.UserService;
import com.example.community.domain.user.dto.UserResponseDto;
import com.example.community.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public User register(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password
    ) {

        return userService.register(username, email, password);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponseDto> me(Authentication authentication){
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("사용자 없음"));

        UserResponseDto response = new UserResponseDto(user);

        return ApiResponse.success(response);
    }


}