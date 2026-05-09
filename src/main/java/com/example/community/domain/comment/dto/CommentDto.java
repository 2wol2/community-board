package com.example.community.domain.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommentDto {

    private Long id;
    private String content;

}