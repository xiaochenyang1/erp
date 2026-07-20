package com.tuowei.erp.inventory.mrp.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.inventory.mrp.service.MrpPlanService;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PostMapping("/run")
    public ApiResponse<MrpRunResponse> run() {
        return ApiResponse.success(mrpPlanService.run());
    }
}
