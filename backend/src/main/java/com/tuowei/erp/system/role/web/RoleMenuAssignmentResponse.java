package com.tuowei.erp.system.role.web;

import java.util.List;

public record RoleMenuAssignmentResponse(
        Long roleId,
        List<Long> menuIds
) {
}
