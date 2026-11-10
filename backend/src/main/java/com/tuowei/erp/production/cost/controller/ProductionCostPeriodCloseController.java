package com.tuowei.erp.production.cost.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.cost.service.ProductionCostPeriodCloseService;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchRequest;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseEvidenceResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodClosePreviewResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/production/cost-close-periods")
public class ProductionCostPeriodCloseController {
    private final ProductionCostPeriodCloseService service;

    public ProductionCostPeriodCloseController(ProductionCostPeriodCloseService service) {
        this.service = service;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_COST_CLOSE)
    @GetMapping
    public ApiResponse<List<ProductionCostPeriodResponse>> listPeriods() {
        return ApiResponse.success(service.listPeriods());
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_COST_CLOSE)
    @GetMapping("/{periodId}/preview")
    public ApiResponse<ProductionCostPeriodClosePreviewResponse> preview(@PathVariable Long periodId) {
        return ApiResponse.success(service.preview(periodId));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_COST_CLOSE)
    @PostMapping("/{periodId}/batch-close")
    @OperationLog(
            module = "production",
            operation = "batch-close-production-cost",
            message = "期间批量关单",
            bizNo = "#result.data.periodMonth"
    )
    public ApiResponse<ProductionCostPeriodCloseBatchResponse> closeBatch(
            @PathVariable Long periodId,
            @Valid @RequestBody ProductionCostPeriodCloseBatchRequest request
    ) {
        return ApiResponse.success(service.closeBatch(periodId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_COST_CLOSE)
    @GetMapping("/{periodId}/evidence")
    public ApiResponse<PageResponse<ProductionCostPeriodCloseEvidenceResponse>> evidence(
            @PathVariable Long periodId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.success(service.evidence(periodId, pageNo, pageSize));
    }
}
