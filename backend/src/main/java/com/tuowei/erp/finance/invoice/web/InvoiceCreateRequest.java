package com.tuowei.erp.finance.invoice.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceCreateRequest(
        @NotBlank(message = "invoiceType不能为空") String invoiceType,
        @NotBlank(message = "partnerName不能为空") String partnerName,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "发票金额必须大于0") BigDecimal amount,
        @NotNull(message = "taxAmount不能为空") @DecimalMin(value = "0.00", message = "税额不能为负") BigDecimal taxAmount,
        @NotNull(message = "invoiceDate不能为空") LocalDate invoiceDate,
        String relatedBizType,
        Long relatedBizId,
        String remark
) {
}
