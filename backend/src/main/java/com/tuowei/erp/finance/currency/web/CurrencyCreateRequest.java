package com.tuowei.erp.finance.currency.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CurrencyCreateRequest(
        @NotBlank(message = "currencyCode不能为空") String currencyCode,
        @NotBlank(message = "currencyName不能为空") String currencyName,
        String currencySymbol,
        @Min(value = 0, message = "decimalPlaces不能小于0")
        @Max(value = 6, message = "decimalPlaces不能大于6")
        Integer decimalPlaces
) {
}
