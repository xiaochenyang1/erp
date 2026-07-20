package com.tuowei.erp.production.workcenter.web;

import jakarta.validation.constraints.NotBlank;

public record ProductionWorkCenterCreateRequest(
        @NotBlank(message = "工作中心编码不能为空") String workCenterCode,
        @NotBlank(message = "工作中心名称不能为空") String workCenterName,
        String remark
) {
}
