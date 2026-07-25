package com.tuowei.erp.inventory.mrp.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.mrp.service.MrpPlanService;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpRunSummaryResponse;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/mrp")
public class MrpPlanController {

    private final MrpPlanService mrpPlanService;

    public MrpPlanController(MrpPlanService mrpPlanService) {
        this.mrpPlanService = mrpPlanService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_MRP_RUN + " or " + PermissionCodes.HAS_INVENTORY_MRP_VIEW)
    @OperationLog(module = "inventory", operation = "mrp-run", bizNo = "#result.data.runNo")
    @PostMapping("/run")
    public ApiResponse<MrpRunResponse> run() {
        return ApiResponse.success(mrpPlanService.run());
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_MRP_VIEW)
    @GetMapping("/runs")
    public ApiResponse<PageResponse<MrpRunSummaryResponse>> listRuns(MrpRunPageQuery query) {
        return ApiResponse.success(mrpPlanService.listRuns(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_MRP_VIEW)
    @GetMapping("/runs/{id}")
    public ApiResponse<MrpRunResponse> getRun(@PathVariable Long id) {
        return ApiResponse.success(mrpPlanService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_MRP_CONVERT + " or " + PermissionCodes.HAS_INVENTORY_MRP_RUN)
    @OperationLog(module = "inventory", operation = "mrp-convert", bizNo = "#lineId")
    @PostMapping("/runs/{runId}/lines/{lineId}/convert")
    public ApiResponse<MrpSuggestionLineResponse> convertLine(
            @PathVariable Long runId,
            @PathVariable Long lineId,
            @RequestBody(required = false) MrpConvertLineRequest request
    ) {
        return ApiResponse.success(mrpPlanService.convertLine(runId, lineId, request));
    }
}
