package com.tuowei.erp.system.user.web;

import java.util.List;

/**
 * 用户数据范围：前 6 个字段为用户级配置；effective* 为与角色范围并集后的生效结果。
 */
public record UserDataScopeResponse(
        Long userId,
        boolean hasAllScope,
        boolean deptScoped,
        boolean postScoped,
        boolean selfScoped,
        List<Long> warehouseIds,
        boolean effectiveHasAllScope,
        boolean effectiveDeptScoped,
        boolean effectivePostScoped,
        boolean effectiveSelfScoped,
        List<Long> effectiveWarehouseIds
) {
}
