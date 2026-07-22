package com.tuowei.erp.system.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "realName不能为空")
        @Size(max = 64, message = "realName长度不能超过64")
        String realName,
        @Email(message = "email格式不正确")
        @Size(max = 128, message = "email长度不能超过128")
        String email,
        @Size(max = 32, message = "mobile长度不能超过32")
        String mobile,
        @Size(max = 512, message = "avatar长度不能超过512")
        String avatar,
        @Size(max = 16, message = "locale长度不能超过16")
        String locale,
        @Size(max = 64, message = "timeZone长度不能超过64")
        String timeZone
) {
}
