package com.example.community.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {

        ErrorCode code = e.getErrorCode();

        ErrorResponse error = new ErrorResponse(code.getMessage());

        return ResponseEntity
                .status(code.getStatus())
                .body(error);
    }
}
