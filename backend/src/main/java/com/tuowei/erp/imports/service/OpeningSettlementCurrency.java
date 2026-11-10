package com.tuowei.erp.imports.service;

import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Currency snapshot for opening receivable and payable import rows. */
final class OpeningSettlementCurrency {

    private OpeningSettlementCurrency() {
    }

    record Snapshot(
            String currencyCode,
            BigDecimal exchangeRate,
            BigDecimal baseOriginalAmount,
            BigDecimal baseSettledAmount
    ) {
    }

    static Snapshot resolve(
            String currencyRaw,
            String rateRaw,
            BigDecimal originalAmount,
            BigDecimal settledAmount,
            List<ImportRowErrorResponse> errors
    ) {
        boolean hasCurrency = StringUtils.hasText(currencyRaw);
        boolean hasRate = StringUtils.hasText(rateRaw);
        String currency = hasCurrency
                ? currencyRaw.trim().toUpperCase(Locale.ROOT)
                : CurrencyAmountSupport.LEGACY_CURRENCY;
        if (hasCurrency && !currency.matches("[A-Z]{3}")) {
            errors.add(new ImportRowErrorResponse("currency_code", "币种必须是3位字母代码"));
        }
        BigDecimal rate = CurrencyAmountSupport.ONE;
        boolean rateValid = true;
        if (hasRate) {
            try {
                rate = new BigDecimal(rateRaw.trim());
                if (rate.signum() <= 0) {
                    errors.add(new ImportRowErrorResponse("exchange_rate", "汇率必须大于0"));
                    rateValid = false;
                }
            } catch (NumberFormatException ex) {
                errors.add(new ImportRowErrorResponse("exchange_rate", "汇率格式不正确"));
                rateValid = false;
            }
        } else if (hasCurrency && !CurrencyAmountSupport.LEGACY_CURRENCY.equals(currency)) {
            errors.add(new ImportRowErrorResponse("exchange_rate", "外币必须填写汇率"));
            rateValid = false;
        }
        if (!hasCurrency && hasRate && rateValid && rate.compareTo(BigDecimal.ONE) != 0) {
            errors.add(new ImportRowErrorResponse("currency_code", "填写非1汇率时必须填写币种"));
        }
        BigDecimal baseOriginal = originalAmount == null || !rateValid
                ? null
                : CurrencyAmountSupport.base(originalAmount, rate);
        BigDecimal baseSettled = settledAmount == null || !rateValid
                ? null
                : CurrencyAmountSupport.base(settledAmount, rate);
        return new Snapshot(currency, rateValid ? rate : null, baseOriginal, baseSettled);
    }
}
