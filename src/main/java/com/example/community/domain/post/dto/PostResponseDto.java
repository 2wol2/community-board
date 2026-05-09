package com.example.community.domain.post.dto;

import com.example.community.domain.comment.dto.CommentDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostResponseDto {

    private Long id;
    private String title;
    private String content;
    private List<CommentDto> comments;
    private Long viewCount;
}