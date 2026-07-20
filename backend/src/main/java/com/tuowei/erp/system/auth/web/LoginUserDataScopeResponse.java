package com.tuowei.erp.system.auth.web;

import java.util.List;

public record LoginUserDataScopeResponse(
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        List<Long> warehouseIds
) {
}
