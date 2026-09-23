package com.tuowei.erp.finance.fx.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRevaluationResponse(
        Long id,
        Long periodId,
        String periodMonth,
        String status,
        BigDecimal openOriginalTotal,
        BigDecimal arAdjustment,
        BigDecimal apAdjustment,
        LocalDate revaluationDate,
        LocalDate reversalDate,
        boolean alreadyPosted
) {
}
