package com.tuowei.erp.sales.delivery.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLogisticsUpdateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for sales delivery commands and queries. */
@Service
public class SalesDeliveryService {

    private final SalesDeliveryQueryService salesDeliveryQueryService;
    private final SalesDeliveryPostingService salesDeliveryPostingService;
    private final SalesDeliveryCommandService salesDeliveryCommandService;

    public SalesDeliveryService(
            SalesDeliveryQueryService salesDeliveryQueryService,
            SalesDeliveryPostingService salesDeliveryPostingService,
            SalesDeliveryCommandService salesDeliveryCommandService
    ) {
        this.salesDeliveryQueryService = salesDeliveryQueryService;
        this.salesDeliveryPostingService = salesDeliveryPostingService;
        this.salesDeliveryCommandService = salesDeliveryCommandService;
    }

    @Transactional
    public SalesDeliveryResponse create(SalesDeliveryCreateRequest request) {
        return salesDeliveryCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDeliveryResponse> list(SalesDeliveryPageQuery query) {
        SalesDeliveryPageQuery safeQuery = query == null ? new SalesDeliveryPageQuery() : query;
        return salesDeliveryQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public SalesDeliveryResponse getById(Long id) {
        return salesDeliveryQueryService.getById(id);
    }

    @Transactional
    public SalesDeliveryResponse update(Long id, SalesDeliveryUpdateRequest request) {
        return salesDeliveryCommandService.update(id, request);
    }

    @Transactional
    public SalesDeliveryResponse cancel(Long id) {
        return salesDeliveryCommandService.cancel(id);
    }

    @Transactional
    public SalesDeliveryResponse post(Long id) {
        return salesDeliveryPostingService.post(id);
    }

    @Transactional
    public SalesDeliveryResponse updateLogistics(Long id, SalesDeliveryLogisticsUpdateRequest request) {
        return salesDeliveryCommandService.updateLogistics(id, request);
    }
}
