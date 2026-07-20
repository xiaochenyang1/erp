package com.tuowei.erp.system.user.web;

public record UserResponse(
        Long id,
        String username,
        String employeeNo,
        String realName,
        String email,
        String mobile,
        String avatar,
        Long deptId,
        Long postId,
        String status,
        String remark
) {
}
