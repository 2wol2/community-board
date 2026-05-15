package com.example.community.global.exception;

public record ErrorResponse(
        String code,
        String message
) {
}
