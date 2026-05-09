package com.example.community.domain.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostListDto {

    private Long id;
    private String title;
    private String content;
}
