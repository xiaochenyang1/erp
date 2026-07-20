package com.tuowei.erp.purchase.returnorder.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseReturnUpdateRequest(
        @NotNull(message = "receiptId不能为空") Long receiptId,
        @NotNull(message = "returnDate不能为空") LocalDate returnDate,
        String remark,
        @Valid @NotEmpty(message = "lines不能为空") List<PurchaseReturnLineRequest> lines
) {
}
