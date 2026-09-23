package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 账龄分析。
 *
 * <p>总额与账龄桶金额都是**本位币**口径（{@code baseCurrencyCode}）；原币敞口按币种单列，
 * 避免跨币种直接相加。
 */
public record FinanceAgingSummaryResponse(
        LocalDate asOfDate,
        String baseCurrencyCode,
        BigDecimal receivableTotal,
        BigDecimal payableTotal,
        List<FinanceAgingBucketResponse> receivableBuckets,
        List<FinanceAgingBucketResponse> payableBuckets,
        List<FinanceAgingCurrencyExposureResponse> receivableCurrencyExposures,
        List<FinanceAgingCurrencyExposureResponse> payableCurrencyExposures,
        List<FinanceAgingOpenItemResponse> overdueReceivables,
        List<FinanceAgingOpenItemResponse> overduePayables
) {
}
