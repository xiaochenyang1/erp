package com.tuowei.erp.production.workcenter.web;

public record ProductionWorkCenterResponse(
        Long id,
        String workCenterCode,
        String workCenterName,
        String status,
        String remark
) {
}
