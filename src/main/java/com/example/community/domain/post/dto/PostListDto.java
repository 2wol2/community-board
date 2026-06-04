package com.example.community.domain.post.dto;

import com.example.community.domain.post.Category;
import com.example.community.domain.post.RecruitStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostListDto {

    private Long id;
    private String title;
    private String content;

    // 스터디 관련 필드 (nullable)
    private Category category;
    private RecruitStatus status;
    private Integer maxMembers;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;

    // 기존 코드 호환성을 위한 간단한 생성자
    public PostListDto(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    // Builder 사용을 위한 전체 생성자
    public PostListDto(Long id, String title, String content, Category category,
                       RecruitStatus status, Integer maxMembers,
                       LocalDateTime deadline, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = status;
        this.maxMembers = maxMembers;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

}

