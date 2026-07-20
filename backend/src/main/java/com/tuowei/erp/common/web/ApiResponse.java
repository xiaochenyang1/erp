package com.tuowei.erp.common.web;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data);
    }
}
