package com.tuowei.erp.imports.service;

import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningSettlementCurrencyTest {

    @Test
    void blankCurrencyUsesBookCurrencyAndKeepsHistoricalFilesReadable() {
        List<ImportRowErrorResponse> errors = new ArrayList<>();

        OpeningSettlementCurrency.Snapshot snapshot = OpeningSettlementCurrency.resolve(
                null, null, new BigDecimal("500.00"), new BigDecimal("0"), errors);

        assertThat(errors).isEmpty();
        assertThat(snapshot.currencyCode()).isEqualTo("CNY");
        assertThat(snapshot.exchangeRate()).isEqualByComparingTo("1");
        assertThat(snapshot.baseOriginalAmount()).isEqualByComparingTo("500.000000");
        assertThat(snapshot.baseSettledAmount()).isEqualByComparingTo("0");
    }

    @Test
    void foreignOpeningUsesTheSuppliedRateForBothOpenAndSettledAmounts() {
        List<ImportRowErrorResponse> errors = new ArrayList<>();

        OpeningSettlementCurrency.Snapshot snapshot = OpeningSettlementCurrency.resolve(
                " usd ", "7.2", new BigDecimal("100.00"), new BigDecimal("20.00"), errors);

        assertThat(errors).isEmpty();
        assertThat(snapshot.currencyCode()).isEqualTo("USD");
        assertThat(snapshot.exchangeRate()).isEqualByComparingTo("7.2");
        assertThat(snapshot.baseOriginalAmount()).isEqualByComparingTo("720.000000");
        assertThat(snapshot.baseSettledAmount()).isEqualByComparingTo("144.000000");
    }

    @Test
    void foreignCurrencyWithoutRateAndNonUnitRateWithoutCurrencyAreRejected() {
        List<ImportRowErrorResponse> missingRate = new ArrayList<>();
        OpeningSettlementCurrency.resolve("USD", " ", new BigDecimal("10"), BigDecimal.ZERO, missingRate);
        assertThat(missingRate).extracting(ImportRowErrorResponse::column).containsExactly("exchange_rate");

        List<ImportRowErrorResponse> missingCurrency = new ArrayList<>();
        OpeningSettlementCurrency.resolve("", "7.2", new BigDecimal("10"), BigDecimal.ZERO, missingCurrency);
        assertThat(missingCurrency).extracting(ImportRowErrorResponse::column).containsExactly("currency_code");

        List<ImportRowErrorResponse> badRate = new ArrayList<>();
        OpeningSettlementCurrency.Snapshot snapshot = OpeningSettlementCurrency.resolve(
                "EUR", "0", new BigDecimal("10"), BigDecimal.ZERO, badRate);
        assertThat(badRate).extracting(ImportRowErrorResponse::column).containsExactly("exchange_rate");
        assertThat(snapshot.exchangeRate()).isNull();
        assertThat(snapshot.baseOriginalAmount()).isNull();
    }
}
