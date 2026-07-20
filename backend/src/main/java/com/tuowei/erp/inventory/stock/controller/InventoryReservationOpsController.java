package com.tuowei.erp.inventory.stock.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.service.InventoryReservationOpsService;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationDetailResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationManualReleaseRequest;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/reservations")
public class InventoryReservationOpsController {

    private final InventoryReservationOpsService inventoryReservationOpsService;

    public InventoryReservationOpsController(InventoryReservationOpsService inventoryReservationOpsService) {
        this.inventoryReservationOpsService = inventoryReservationOpsService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InventoryReservationResponse>> listReservations(InventoryReservationPageQuery query) {
        return ApiResponse.success(inventoryReservationOpsService.listReservations(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_VIEW)
    @GetMapping("/summary")
    public ApiResponse<List<InventoryReservationSummaryResponse>> summary(InventoryReservationSummaryQuery query) {
        return ApiResponse.success(inventoryReservationOpsService.summary(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_VIEW)
    @GetMapping("/source")
    public ApiResponse<InventoryReservationSourceResponse> source(InventoryReservationSourceQuery query) {
        return ApiResponse.success(inventoryReservationOpsService.source(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_CHECK)
    @GetMapping("/checks")
    public ApiResponse<List<InventoryReservationCheckIssueResponse>> checks(InventoryReservationCheckQuery query) {
        return ApiResponse.success(inventoryReservationOpsService.checks(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<InventoryReservationDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(inventoryReservationOpsService.getReservation(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_RESERVATION_RELEASE)
    @PostMapping("/{id}/manual-release")
    public ApiResponse<InventoryReservationDetailResponse> manualRelease(
            @PathVariable Long id,
            @Valid @RequestBody InventoryReservationManualReleaseRequest request
    ) {
        return ApiResponse.success(inventoryReservationOpsService.manualRelease(id, request));
    }
}
