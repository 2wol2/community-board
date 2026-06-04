package com.example.community.domain.application.dto;

import com.example.community.domain.application.StudyApplication;
import com.example.community.domain.application.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class StudyApplicationResponseDto {

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String username;
    private ApplicationStatus status;
    private String message;
    private LocalDateTime createdAt;

    public static StudyApplicationResponseDto from(StudyApplication application) {
        return StudyApplicationResponseDto.builder()
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
