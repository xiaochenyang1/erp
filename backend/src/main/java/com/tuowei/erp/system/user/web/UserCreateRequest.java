package com.tuowei.erp.system.user.web;

import com.tuowei.erp.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "username不能为空") String username,
        @NotBlank(message = "password不能为空")
        @StrongPassword
        String password,
        String employeeNo,
        @NotBlank(message = "realName不能为空") String realName,
        @Email(message = "email格式不正确")
        @Size(max = 128, message = "email长度不能超过128")
        String email,
        String mobile,
        @Size(max = 512, message = "avatar长度不能超过512")
        String avatar,
        Long deptId,
        Long postId,
        String remark
) {
}
