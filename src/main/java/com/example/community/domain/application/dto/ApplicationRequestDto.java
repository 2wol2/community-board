package com.example.community.domain.application.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRequestDto {

    @Size(max = 500, message = "지원 메시지는 500자를 초과할 수 없습니다")
    private String message;

}
