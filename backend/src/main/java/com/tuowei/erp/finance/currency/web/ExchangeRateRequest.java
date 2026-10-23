package com.tuowei.erp.finance.currency.web;
import jakarta.validation.constraints.*;
import java.math.BigDecimal; import java.time.LocalDate;
public record ExchangeRateRequest(@NotBlank String fromCurrencyCode,@NotBlank String toCurrencyCode,@NotNull @DecimalMin("0.000000000001") BigDecimal rate,@NotNull LocalDate effectiveFrom,LocalDate effectiveTo) {}
