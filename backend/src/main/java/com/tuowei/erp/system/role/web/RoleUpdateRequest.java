package com.tuowei.erp.system.role.web;

import jakarta.validation.constraints.NotBlank;

public record RoleUpdateRequest(
        @NotBlank(message = "roleName不能为空") String roleName,
        String remark
) {
}
