package com.tuowei.erp.system.role.web;

import java.util.List;

public record RoleDataScopeAssignRequest(
        Boolean hasAllScope,
        Boolean deptScoped,
        Boolean postScoped,
        Boolean selfScoped,
        List<Long> warehouseIds
) {
}
