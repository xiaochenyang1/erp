package com.tuowei.erp.system.user.web;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UserRoleAssignRequest(
        @NotEmpty(message = "roleIds不能为空") List<Long> roleIds
) {
}
