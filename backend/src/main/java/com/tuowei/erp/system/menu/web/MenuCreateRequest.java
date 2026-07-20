package com.tuowei.erp.system.menu.web;

import jakarta.validation.constraints.NotBlank;

public record MenuCreateRequest(
        Long parentId,
        @NotBlank(message = "menuType不能为空") String menuType,
        @NotBlank(message = "menuCode不能为空") String menuCode,
        @NotBlank(message = "menuName不能为空") String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo
) {
}
