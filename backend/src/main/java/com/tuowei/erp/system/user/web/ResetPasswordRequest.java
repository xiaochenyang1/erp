package com.tuowei.erp.system.user.web;

import com.tuowei.erp.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "newPassword不能为空")
        @StrongPassword
        String newPassword
) {
}
