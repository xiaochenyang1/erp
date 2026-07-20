package com.tuowei.erp.system.readiness.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.readiness.service.ReadinessPreflightService;
import com.tuowei.erp.system.readiness.service.ReadinessService;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessRunDetailResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import com.tuowei.erp.system.readiness.web.ReadinessRunResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/readiness")
public class ReadinessController {

    private final ReadinessService readinessService;
    private final ReadinessPreflightService readinessPreflightService;

    public ReadinessController(ReadinessService readinessService, ReadinessPreflightService readinessPreflightService) {
        this.readinessService = readinessService;
        this.readinessPreflightService = readinessPreflightService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_VIEW)
    @GetMapping("/preflight")
    public ApiResponse<ReadinessPreflightResponse> preflight() {
        return ApiResponse.success(readinessPreflightService.preflight());
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/runs/{id}/preflight-evidence")
    public ApiResponse<ReadinessPreflightResponse> recordPreflightEvidence(@PathVariable Long id) {
        return ApiResponse.success(readinessService.recordPreflightEvidence(id, readinessPreflightService.preflight()));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/runs")
    public ApiResponse<ReadinessRunResponse> createRun(@Valid @RequestBody ReadinessRunCreateRequest request) {
        ReadinessRunResponse response = readinessService.createRun(request);
        if (Boolean.TRUE.equals(request.recordPreflightEvidence())) {
            readinessService.recordPreflightEvidence(response.id(), readinessPreflightService.preflight());
            response = readinessService.detail(response.id()).run();
        }
        return ApiResponse.success(response);
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_VIEW)
    @GetMapping("/runs")
    public ApiResponse<PageResponse<ReadinessRunResponse>> listRuns(ReadinessRunPageQuery query) {
        return ApiResponse.success(readinessService.listRuns(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_VIEW)
    @GetMapping("/runs/{id}")
    public ApiResponse<ReadinessRunDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(readinessService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/runs/{id}/items")
    public ApiResponse<ReadinessItemResponse> addItem(
            @PathVariable Long id,
            @Valid @RequestBody ReadinessItemCreateRequest request
    ) {
        return ApiResponse.success(readinessService.addItem(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/items/{itemId}/evidence")
    public ApiResponse<ReadinessEvidenceResponse> addEvidence(
            @PathVariable Long itemId,
            @Valid @RequestBody ReadinessEvidenceCreateRequest request
    ) {
        return ApiResponse.success(readinessService.addEvidence(itemId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/items/{itemId}/result")
    public ApiResponse<ReadinessItemResponse> markItemResult(
            @PathVariable Long itemId,
            @Valid @RequestBody ReadinessItemResultRequest request
    ) {
        return ApiResponse.success(readinessService.markItemResult(itemId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_DECIDE)
    @PostMapping("/runs/{id}/decision")
    public ApiResponse<ReadinessRunResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody ReadinessDecisionRequest request
    ) {
        return ApiResponse.success(readinessService.decide(id, request));
    }
}
