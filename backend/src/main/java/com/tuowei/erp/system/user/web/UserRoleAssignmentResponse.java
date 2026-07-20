package com.tuowei.erp.system.user.web;

import java.util.List;

public record UserRoleAssignmentResponse(
        Long userId,
        List<Long> roleIds
) {
}
