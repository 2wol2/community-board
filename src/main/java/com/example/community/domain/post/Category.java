package com.example.community.domain.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 스터디 카테고리
 */
@Getter
@RequiredArgsConstructor
public enum Category {
    BACKEND("백엔드", "Spring, Node.js, Django 등"),
    FRONTEND("프론트엔드", "React, Vue, Angular 등"),
    ALGORITHM("알고리즘", "코딩테스트, PS 등"),
    LANGUAGE("언어", "Java, Python, JavaScript 등");

    private final String displayName;
    private final String description;
}
