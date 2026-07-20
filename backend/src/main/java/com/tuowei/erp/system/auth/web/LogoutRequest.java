package com.tuowei.erp.system.auth.web;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "refreshToken不能为空") String refreshToken
) {
}
