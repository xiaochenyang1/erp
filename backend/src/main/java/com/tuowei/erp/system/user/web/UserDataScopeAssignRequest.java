package com.tuowei.erp.system.user.web;

import java.util.List;

public record UserDataScopeAssignRequest(
        Boolean hasAllScope,
        Boolean deptScoped,
        Boolean postScoped,
        Boolean selfScoped,
        List<Long> warehouseIds
) {
}
