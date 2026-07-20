package com.tuowei.erp.system.role.web;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RoleMenuAssignRequest(
        @NotEmpty(message = "menuIds不能为空") List<Long> menuIds
) {
}
