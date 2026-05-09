package com.example.community.domain.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommentResponseDto {

    private Long id;
    private String content;
}