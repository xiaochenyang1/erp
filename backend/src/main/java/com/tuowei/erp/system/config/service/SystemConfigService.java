package com.tuowei.erp.system.config.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for global system configuration queries and commands. */
@Service
public class SystemConfigService {

    private final SystemConfigQueryService systemConfigQueryService;
    private final SystemConfigCommandService systemConfigCommandService;

    public SystemConfigService(
            SystemConfigQueryService systemConfigQueryService,
            SystemConfigCommandService systemConfigCommandService
    ) {
        this.systemConfigQueryService = systemConfigQueryService;
        this.systemConfigCommandService = systemConfigCommandService;
    }

    @Transactional
    public SystemConfigResponse create(SystemConfigCreateRequest request) {
        return systemConfigCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemConfigResponse> list(SystemConfigPageQuery query) {
        return systemConfigQueryService.list(query == null ? new SystemConfigPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public SystemConfigResponse getById(Long id) {
        return systemConfigQueryService.getById(id);
    }

    @Transactional
    public SystemConfigResponse update(Long id, SystemConfigUpdateRequest request) {
        return systemConfigCommandService.update(id, request);
    }

    @Transactional
    public SystemConfigResponse enable(Long id) {
        return systemConfigCommandService.enable(id);
    }

    @Transactional
    public SystemConfigResponse disable(Long id) {
        return systemConfigCommandService.disable(id);
    }
}
