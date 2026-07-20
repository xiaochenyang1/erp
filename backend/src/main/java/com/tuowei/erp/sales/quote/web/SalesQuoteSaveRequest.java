package com.tuowei.erp.sales.quote.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesQuoteSaveRequest(
        @NotNull Long customerId,
        @NotNull LocalDate quoteDate,
        LocalDate validUntil,
        String remark,
        @NotEmpty @Valid List<SalesQuoteLineRequest> lines
) {
}
