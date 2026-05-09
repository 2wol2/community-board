package com.example.community.global.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    public ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "요청 성공");
    }

    public static ApiResponse<?> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }

}