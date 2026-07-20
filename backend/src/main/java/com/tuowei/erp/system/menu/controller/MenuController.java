package com.tuowei.erp.system.menu.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_CREATE)
    @PostMapping
    public ApiResponse<MenuResponse> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.success(menuService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<MenuResponse>> list(MenuPageQuery query) {
        return ApiResponse.success(menuService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_VIEW)
    @GetMapping("/tree")
    public ApiResponse<List<MenuResponse>> tree() {
        return ApiResponse.success(menuService.tree());
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<MenuResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(menuService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<MenuResponse> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.success(menuService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<MenuResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(menuService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_MENU_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<MenuResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(menuService.disable(id));
    }
}
