package com.tuowei.erp.finance.currency;

import com.tuowei.erp.finance.currency.service.CurrencyConversionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyConversionServiceTest {
    private final CurrencyConversionService service = new CurrencyConversionService();

    @Test
    void convertsWithHalfUpRounding() {
        assertEquals(new BigDecimal("12.35"), service.toBaseCurrency(new BigDecimal("10"), new BigDecimal("1.2345"), 2));
        assertEquals(new BigDecimal("10.00"), service.fromBaseCurrency(new BigDecimal("12.345"), new BigDecimal("1.2345"), 2));
    }

    @Test
    void rejectsInvalidRateAndScale() {
        assertThrows(IllegalArgumentException.class, () -> service.toBaseCurrency(BigDecimal.ONE, BigDecimal.ZERO, 2));
        assertThrows(IllegalArgumentException.class, () -> service.fromBaseCurrency(BigDecimal.ONE, BigDecimal.ONE, 9));
    }
}
