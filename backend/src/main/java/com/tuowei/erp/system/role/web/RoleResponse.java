package com.tuowei.erp.system.role.web;

public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        String status,
        String remark
) {
}
