package com.example.community.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 사용자입니다."),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글이 없습니다."),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글이 없습니다."),

    // Auth
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh Token을 찾을 수 없습니다."),

    // Like
    ALREADY_LIKED(HttpStatus.BAD_REQUEST, "이미 좋아요를 눌렀습니다."),
    LIKE_NOT_FOUND(HttpStatus.BAD_REQUEST, "좋아요를 누르지 않았습니다."),

    // Application
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "지원을 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.BAD_REQUEST, "이미 지원한 스터디입니다."),
    RECRUIT_CLOSED(HttpStatus.BAD_REQUEST, "모집이 마감된 스터디입니다."),
    CANNOT_APPLY_OWN_POST(HttpStatus.BAD_REQUEST, "자신의 스터디에는 지원할 수 없습니다."),
    APPLICATION_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "이미 처리된 지원입니다."),

    // Common
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}