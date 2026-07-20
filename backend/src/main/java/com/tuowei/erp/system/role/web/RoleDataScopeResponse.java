package com.tuowei.erp.system.role.web;

import java.util.List;

public record RoleDataScopeResponse(
        Long roleId,
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        List<Long> warehouseIds
) {
}
