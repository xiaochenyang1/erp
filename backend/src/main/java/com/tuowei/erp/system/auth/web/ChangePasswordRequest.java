package com.tuowei.erp.system.auth.web;

import com.tuowei.erp.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "oldPassword不能为空") String oldPassword,
        @NotBlank(message = "newPassword不能为空")
        @StrongPassword
        String newPassword
) {
}
