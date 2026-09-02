package com.tuowei.erp.masterdata.warehouse.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Compatibility facade for warehouse queries, export and commands. */
@Service
public class WarehouseService {

    private final WarehouseQueryService warehouseQueryService;
    private final WarehouseCommandService warehouseCommandService;

    public WarehouseService(WarehouseQueryService warehouseQueryService, WarehouseCommandService warehouseCommandService) {
        this.warehouseQueryService = warehouseQueryService;
        this.warehouseCommandService = warehouseCommandService;
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        return warehouseCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(Long id) {
        return warehouseQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(WarehousePageQuery query) {
        WarehousePageQuery safeQuery = query == null ? new WarehousePageQuery() : query;
        return warehouseQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportWarehouses(WarehousePageQuery query) {
        return warehouseQueryService.exportWarehouses(query);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        return warehouseCommandService.update(id, request);
    }

    @Transactional
    public WarehouseResponse enable(Long id) {
        return warehouseCommandService.enable(id);
    }

    @Transactional
    public WarehouseResponse disable(Long id) {
        return warehouseCommandService.disable(id);
    }
}
