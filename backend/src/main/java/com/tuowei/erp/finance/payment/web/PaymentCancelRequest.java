package com.tuowei.erp.finance.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
        @NotBlank(message = "作废原因不能为空") @Size(max = 255, message = "作废原因不能超过255个字符") String reason
) {
}
