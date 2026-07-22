package com.tuowei.erp.masterdata.warehouse.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseStockSummaryService;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseStockSummaryResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/masterdata/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseStockSummaryService warehouseStockSummaryService;

    public WarehouseController(WarehouseService warehouseService, WarehouseStockSummaryService warehouseStockSummaryService) {
        this.warehouseService = warehouseService;
        this.warehouseStockSummaryService = warehouseStockSummaryService;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_CREATE)
    @PostMapping
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseCreateRequest request) {
        return ApiResponse.success(warehouseService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<WarehouseResponse>> list(WarehousePageQuery query) {
        return ApiResponse.success(warehouseService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(WarehousePageQuery query) {
        return csv("warehouses.csv", warehouseService.exportWarehouses(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<WarehouseResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping("/{id}/stock-summary")
    public ApiResponse<WarehouseStockSummaryResponse> stockSummary(@PathVariable Long id) {
        return ApiResponse.success(warehouseStockSummaryService.summary(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<WarehouseResponse> update(@PathVariable Long id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return ApiResponse.success(warehouseService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<WarehouseResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_WAREHOUSE_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<WarehouseResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.disable(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "warehouses.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
