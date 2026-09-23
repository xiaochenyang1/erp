package com.tuowei.erp.finance.posting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPostingAmountsTest {

    @Test
    void alignsATwoAllocationRoundingGapOntoTheExchangeLeg() {
        SettlementPostingAmounts amounts = SettlementPostingAmounts.fromAllocations(
                new BigDecimal("0.06"),
                new BigDecimal("0.06"),
                BigDecimal.ZERO
        ).alignCashTo(new BigDecimal("0.050000"));

        assertThat(amounts.reliefAmount()).isEqualByComparingTo("0.06");
        assertThat(amounts.fxAmount()).isEqualByComparingTo("-0.01");
        assertThat(amounts.cashAmount()).isEqualByComparingTo("0.05");
    }

    @Test
    void leavesDifferencesLargerThanFiveCentsUntouched() {
        SettlementPostingAmounts original = SettlementPostingAmounts.fromAllocations(
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO
        );

        SettlementPostingAmounts aligned = original.alignCashTo(new BigDecimal("0.90"));

        assertThat(aligned.fxAmount()).isEqualByComparingTo("0.00");
        assertThat(aligned.cashAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    void ignoresAMissingOrZeroDocumentBase() {
        SettlementPostingAmounts original = SettlementPostingAmounts.fromAllocations(
                new BigDecimal("0.06"),
                new BigDecimal("0.06"),
                BigDecimal.ZERO
        );

        assertThat(original.alignCashTo(null).cashAmount()).isEqualByComparingTo("0.06");
        assertThat(original.alignCashTo(BigDecimal.ZERO).cashAmount()).isEqualByComparingTo("0.06");
    }
}
