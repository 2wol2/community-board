package com.example.community.domain.application.dto;

import com.example.community.domain.application.Application;
import com.example.community.domain.application.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ApplicationResponseDto {

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String username;
    private ApplicationStatus status;
    private String message;
    private LocalDateTime createdAt;

    public static ApplicationResponseDto from(Application application) {
        return ApplicationResponseDto.builder()
                .id(application.getId())
                .postId(application.getPost().getId())
                .postTitle(application.getPost().getTitle())
                .userId(application.getUser().getId())
                .username(application.getUser().getUsername())
                .status(application.getStatus())
                .message(application.getMessage())
                .createdAt(application.getCreatedAt())
                .build();
    }

}
