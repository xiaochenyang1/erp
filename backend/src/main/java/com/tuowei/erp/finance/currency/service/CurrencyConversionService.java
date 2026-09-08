package com.tuowei.erp.finance.currency.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Centralizes deterministic monetary conversion for new multi-currency documents. */
@Service
public class CurrencyConversionService {

    public BigDecimal toBaseCurrency(BigDecimal amount, BigDecimal rate, int scale) {
        if (amount == null || rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("amount and positive rate are required");
        }
        if (scale < 0 || scale > 8) {
            throw new IllegalArgumentException("scale must be between 0 and 8");
        }
        return amount.multiply(rate).setScale(scale, RoundingMode.HALF_UP);
    }

    public BigDecimal fromBaseCurrency(BigDecimal amount, BigDecimal rate, int scale) {
        if (amount == null || rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("amount and positive rate are required");
        }
        if (scale < 0 || scale > 8) {
            throw new IllegalArgumentException("scale must be between 0 and 8");
        }
        return amount.divide(rate, scale, RoundingMode.HALF_UP);
    }
}
