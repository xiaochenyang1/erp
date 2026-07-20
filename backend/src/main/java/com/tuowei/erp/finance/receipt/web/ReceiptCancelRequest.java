package com.tuowei.erp.finance.receipt.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceiptCancelRequest(
        @NotBlank(message = "作废原因不能为空") @Size(max = 255, message = "作废原因不能超过255个字符") String reason
) {
}
