package com.tuowei.erp.masterdata.warehouse.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Warehouse facade - delegates reads to {@link WarehouseQueryService} and writes to
 * {@link WarehousePostingService}, keeping a thin entry point for controllers.
 */
@Service
public class WarehouseService {

    private final WarehouseQueryService warehouseQueryService;
    private final WarehousePostingService warehousePostingService;

    public WarehouseService(WarehouseQueryService warehouseQueryService, WarehousePostingService warehousePostingService) {
        this.warehouseQueryService = warehouseQueryService;
        this.warehousePostingService = warehousePostingService;
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        return warehousePostingService.create(request);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(Long id) {
        return warehouseQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(WarehousePageQuery query) {
        return warehouseQueryService.list(query);
    }

    public StreamingResponseBody exportWarehouses(WarehousePageQuery query) {
        return warehouseQueryService.exportWarehouses(query);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        return warehousePostingService.update(id, request);
    }

    @Transactional
    public WarehouseResponse enable(Long id) {
        return warehousePostingService.enable(id);
    }

    @Transactional
    public WarehouseResponse disable(Long id) {
        return warehousePostingService.disable(id);
    }
}
