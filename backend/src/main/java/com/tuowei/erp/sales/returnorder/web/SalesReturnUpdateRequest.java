package com.tuowei.erp.sales.returnorder.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesReturnUpdateRequest(
        @NotNull(message = "deliveryId不能为空") Long deliveryId,
        @NotNull(message = "returnDate不能为空") LocalDate returnDate,
        String remark,
        @Valid @NotEmpty(message = "lines不能为空") List<SalesReturnLineRequest> lines
) {
}
