package com.tuowei.erp.finance.currency.web;
import java.math.BigDecimal; import java.time.LocalDate;
public record ExchangeRateResponse(Long id,String fromCurrencyCode,String toCurrencyCode,BigDecimal rate,LocalDate effectiveFrom,LocalDate effectiveTo,String status) {}
