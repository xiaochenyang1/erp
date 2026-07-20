package com.tuowei.erp.qc.inspection.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QcInspectionUpdateLineRequest(
        @NotNull(message = "lineId不能为空") Long lineId,
        @NotNull(message = "inspectedQty不能为空") BigDecimal inspectedQty,
        String defectReason,
        String remark
) {
}
