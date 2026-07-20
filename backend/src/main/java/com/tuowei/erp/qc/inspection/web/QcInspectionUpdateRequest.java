package com.tuowei.erp.qc.inspection.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record QcInspectionUpdateRequest(
        @NotNull(message = "inspectionDate不能为空") LocalDate inspectionDate,
        String remark,
        @Valid List<QcInspectionUpdateLineRequest> lines
) {
}
