package com.tuowei.erp.production.order.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.completion.service.ProductionCompletionReversalService;
import com.tuowei.erp.production.completion.service.ProductionCompletionService;
import com.tuowei.erp.production.issue.service.ProductionIssueService;
import com.tuowei.erp.production.order.web.ProductionCompletionReversalRequest;
import com.tuowei.erp.production.order.web.ProductionCompletionRequest;
import com.tuowei.erp.production.order.web.ProductionIssueRequest;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionReturnRequest;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import com.tuowei.erp.production.returnmaterial.service.ProductionReturnService;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.operation.web.ProductionOperationReportRequest;
import com.tuowei.erp.production.operation.web.ProductionOrderOperationResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/orders")
public class ProductionOrderController {

    private final ProductionOrderService productionOrderService;
    private final ProductionIssueService productionIssueService;
    private final ProductionCompletionService productionCompletionService;
    private final ProductionCompletionReversalService productionCompletionReversalService;
    private final ProductionReturnService productionReturnService;
    private final ProductionOperationService productionOperationService;

    public ProductionOrderController(
            ProductionOrderService productionOrderService,
            ProductionIssueService productionIssueService,
            ProductionCompletionService productionCompletionService,
            ProductionCompletionReversalService productionCompletionReversalService,
            ProductionReturnService productionReturnService,
            ProductionOperationService productionOperationService
    ) {
        this.productionOrderService = productionOrderService;
        this.productionIssueService = productionIssueService;
        this.productionCompletionService = productionCompletionService;
        this.productionCompletionReversalService = productionCompletionReversalService;
        this.productionReturnService = productionReturnService;
        this.productionOperationService = productionOperationService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_CREATE)
    @PostMapping
    public ApiResponse<ProductionOrderResponse> create(@Valid @RequestBody ProductionOrderCreateRequest request) {
        return ApiResponse.success(productionOrderService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionOrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductionOrderUpdateRequest request
    ) {
        return ApiResponse.success(productionOrderService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionOrderResponse>> list(ProductionOrderPageQuery query) {
        return ApiResponse.success(productionOrderService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionOrderService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_RELEASE)
    @PostMapping("/{id}/release")
    public ApiResponse<ProductionOrderResponse> release(@PathVariable Long id) {
        return ApiResponse.success(productionOrderService.release(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_CANCEL)
    @PostMapping("/{id}/cancel")
    public ApiResponse<ProductionOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(productionOrderService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_ISSUE)
    @PostMapping("/{id}/issue")
    public ApiResponse<ProductionOrderResponse> issue(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ProductionIssueRequest request
    ) {
        return ApiResponse.success(productionIssueService.issue(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_VIEW)
    @GetMapping("/{id}/operations")
    public ApiResponse<java.util.List<ProductionOrderOperationResponse>> operations(@PathVariable Long id) {
        return ApiResponse.success(productionOperationService.listByOrder(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_REPORT)
    @PostMapping("/{id}/operations/{operationId}/report")
    public ApiResponse<ProductionOrderOperationResponse> reportOperation(
            @PathVariable Long id,
            @PathVariable Long operationId,
            @Valid @RequestBody ProductionOperationReportRequest request
    ) {
        return ApiResponse.success(productionOperationService.report(id, operationId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_COMPLETE)
    @PostMapping("/{id}/complete")
    public ApiResponse<ProductionOrderResponse> complete(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ProductionCompletionRequest request
    ) {
        return ApiResponse.success(productionCompletionService.complete(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_REVERSE_COMPLETION)
    @PostMapping("/{id}/reverse-completion")
    public ApiResponse<ProductionOrderResponse> reverseCompletion(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ProductionCompletionReversalRequest request
    ) {
        return ApiResponse.success(productionCompletionReversalService.reverseCompletion(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ORDER_RETURN)
    @PostMapping("/{id}/return-materials")
    public ApiResponse<ProductionOrderResponse> returnMaterials(
            @PathVariable Long id,
            @Valid @RequestBody ProductionReturnRequest request
    ) {
        return ApiResponse.success(productionReturnService.returnMaterials(id, request));
    }
}
