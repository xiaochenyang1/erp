package com.tuowei.erp.qc.inspection.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QcInspectionJudgeLineRequest(
        @NotNull(message = "lineId不能为空") Long lineId,
        @NotNull(message = "qualifiedQty不能为空") BigDecimal qualifiedQty,
        @NotNull(message = "unqualifiedQty不能为空") BigDecimal unqualifiedQty,
        String defectReason
) {
}
