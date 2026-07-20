package com.tuowei.erp.production.workcenter.web;

import jakarta.validation.constraints.NotBlank;

public record ProductionWorkCenterUpdateRequest(
        @NotBlank(message = "工作中心名称不能为空") String workCenterName,
        String remark
) {
}
