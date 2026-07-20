package com.tuowei.erp.common.security;

import java.util.Set;

public record DataScopeSnapshot(
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        Set<Long> warehouseIds
) {

    public DataScopeSnapshot {
        warehouseIds = Set.copyOf(warehouseIds);
    }

    public static DataScopeSnapshot all() {
        return new DataScopeSnapshot(true, false, false, false, Set.of());
    }

    public static DataScopeSnapshot none() {
        return new DataScopeSnapshot(false, false, false, false, Set.of());
    }
}
