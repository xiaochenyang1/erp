package com.tuowei.erp.system.menu.web;

import java.util.List;

public record MenuResponse(
        Long id,
        Long parentId,
        String menuType,
        String menuCode,
        String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo,
        Integer visibleFlag,
        String status,
        List<MenuResponse> children
) {
}
