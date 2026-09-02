package com.tuowei.erp.production.routing.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for production routing queries and commands. */
@Service
public class ProductionRoutingService {

    private final ProductionRoutingQueryService routingQueryService;
    private final ProductionRoutingCommandService routingCommandService;

    public ProductionRoutingService(
            ProductionRoutingQueryService routingQueryService,
            ProductionRoutingCommandService routingCommandService
    ) {
        this.routingQueryService = routingQueryService;
        this.routingCommandService = routingCommandService;
    }

    @Transactional
    public ProductionRoutingResponse create(ProductionRoutingCreateRequest request) {
        return routingCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public ProductionRoutingResponse getById(Long id) {
        return routingQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionRoutingResponse> list(ProductionRoutingPageQuery query) {
        ProductionRoutingPageQuery safeQuery = query == null ? new ProductionRoutingPageQuery() : query;
        return routingQueryService.list(safeQuery);
    }

    @Transactional
    public ProductionRoutingResponse update(Long id, ProductionRoutingUpdateRequest request) {
        return routingCommandService.update(id, request);
    }

    @Transactional
    public ProductionRoutingResponse enable(Long id) {
        return routingCommandService.enable(id);
    }

    @Transactional
    public ProductionRoutingResponse disable(Long id) {
        return routingCommandService.disable(id);
    }
}
