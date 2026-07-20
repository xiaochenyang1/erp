package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinanceAgingSummaryResponse(
        LocalDate asOfDate,
        BigDecimal receivableTotal,
        BigDecimal payableTotal,
        List<FinanceAgingBucketResponse> receivableBuckets,
        List<FinanceAgingBucketResponse> payableBuckets,
        List<FinanceAgingOpenItemResponse> overdueReceivables,
        List<FinanceAgingOpenItemResponse> overduePayables
) {
}
