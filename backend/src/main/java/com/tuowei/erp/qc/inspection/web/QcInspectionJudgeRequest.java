package com.tuowei.erp.qc.inspection.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record QcInspectionJudgeRequest(
        @NotEmpty(message = "判定明细不能为空") @Valid List<QcInspectionJudgeLineRequest> lines
) {
}
