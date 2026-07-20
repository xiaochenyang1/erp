package com.tuowei.erp.system.auth.web;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        LoginUserResponse user,
        List<String> permissions
) {
}
