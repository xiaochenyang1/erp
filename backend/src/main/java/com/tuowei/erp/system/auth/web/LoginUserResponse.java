package com.tuowei.erp.system.auth.web;

public record LoginUserResponse(
        Long id,
        String username,
        String realName,
        LoginUserDataScopeResponse dataScope
) {
}
