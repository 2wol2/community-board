package com.example.community.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글이 없습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),
    ALREADY_LIKED(HttpStatus.BAD_REQUEST, "이미 좋아요를 눌렀습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}