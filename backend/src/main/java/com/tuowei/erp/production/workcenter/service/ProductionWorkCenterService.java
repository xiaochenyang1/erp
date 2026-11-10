package com.tuowei.erp.production.workcenter.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade delegating work-center reads to the query service and writes to the command service. */
@Service
public class ProductionWorkCenterService {

    private final ProductionWorkCenterQueryService queryService;
    private final ProductionWorkCenterCommandService commandService;

    public ProductionWorkCenterService(
            ProductionWorkCenterQueryService queryService,
            ProductionWorkCenterCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public ProductionWorkCenterResponse create(ProductionWorkCenterCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional
    public ProductionWorkCenterResponse update(Long id, ProductionWorkCenterUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional(readOnly = true)
    public ProductionWorkCenterResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionWorkCenterResponse> list(ProductionWorkCenterPageQuery query) {
        ProductionWorkCenterPageQuery safeQuery = query == null ? new ProductionWorkCenterPageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional
    public ProductionWorkCenterResponse enable(Long id) {
        return commandService.enable(id);
    }

    @Transactional
    public ProductionWorkCenterResponse disable(Long id) {
        return commandService.disable(id);
    }
}
