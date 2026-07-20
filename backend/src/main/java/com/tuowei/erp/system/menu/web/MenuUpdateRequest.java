package com.tuowei.erp.system.menu.web;

import jakarta.validation.constraints.NotBlank;

public record MenuUpdateRequest(
        @NotBlank(message = "menuName不能为空") String menuName,
        String path,
        String component,
        String permission,
        Integer sortNo
) {
}
