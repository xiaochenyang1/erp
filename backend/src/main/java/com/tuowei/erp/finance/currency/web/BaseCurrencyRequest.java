package com.tuowei.erp.finance.currency.web;

import jakarta.validation.constraints.NotBlank;

public record BaseCurrencyRequest(
        @NotBlank(message = "currencyCode不能为空") String currencyCode
) {
}
