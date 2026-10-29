package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;

/**
 * 单一币种的未结清敞口：原币合计与按子账入账汇率折算的本位币合计。
 *
 * <p>账龄桶与总额一律按本位币汇总，跨币种相加才有意义；原币敞口单独按币种列出。
 */
public record FinanceAgingCurrencyExposureResponse(
        String currencyCode,
        long count,
        BigDecimal originalAmount,
        BigDecimal baseAmount
) {
}
