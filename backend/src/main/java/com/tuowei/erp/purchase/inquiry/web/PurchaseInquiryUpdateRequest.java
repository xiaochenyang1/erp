package com.tuowei.erp.purchase.inquiry.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseInquiryUpdateRequest(
        @NotNull(message = "inquiryDate不能为空") LocalDate inquiryDate,
        String title,
        String remark,
        @Valid @NotEmpty(message = "lines不能为空") List<PurchaseInquiryLineRequest> lines
) {
}
