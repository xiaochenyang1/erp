package com.tuowei.erp.system.auth.web;

import java.util.List;

public record UserInfoResponse(
        Long id,
        String username,
        String realName,
        String email,
        String mobile,
        String avatar,
        List<String> roles,
        List<String> permissions
) {
}
