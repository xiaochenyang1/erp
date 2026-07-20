package com.tuowei.erp.common.security;

public record CurrentUser(
        Long userId,
        Long companyId,
        Long accountBookId,
        Long deptId,
        Long postId,
        String username,
        String realName
) {
}
