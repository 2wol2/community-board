package com.example.community.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RefreshRequestDto {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}
