package com.tuowei.erp.system.role.web;

import jakarta.validation.constraints.NotBlank;

public record RoleCreateRequest(
        @NotBlank(message = "roleCode不能为空") String roleCode,
        @NotBlank(message = "roleName不能为空") String roleName,
        String remark
) {
}
