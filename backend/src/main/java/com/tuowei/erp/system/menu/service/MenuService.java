package com.tuowei.erp.system.menu.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for menu queries and commands. */
@Service
public class MenuService {

    private final MenuQueryService menuQueryService;
    private final MenuCommandService menuCommandService;

    public MenuService(MenuQueryService menuQueryService, MenuCommandService menuCommandService) {
        this.menuQueryService = menuQueryService;
        this.menuCommandService = menuCommandService;
    }

    @Transactional
    public MenuResponse create(MenuCreateRequest request) {
        return menuCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuResponse> list(MenuPageQuery query) {
        return menuQueryService.list(query == null ? new MenuPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> tree() {
        return menuQueryService.tree();
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> runtimeTreeForCurrentUser() {
        return menuQueryService.runtimeTreeForCurrentUser();
    }

    @Transactional(readOnly = true)
    public MenuResponse getById(Long id) {
        return menuQueryService.getById(id);
    }

    @Transactional
    public MenuResponse update(Long id, MenuUpdateRequest request) {
        return menuCommandService.update(id, request);
    }

    @Transactional
    public MenuResponse enable(Long id) {
        return menuCommandService.enable(id);
    }

    @Transactional
    public MenuResponse disable(Long id) {
        return menuCommandService.disable(id);
    }
}
