package com.tuowei.erp.finance.voucher.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualVoucherCancelRequest(
        @NotBlank(message = "作废原因不能为空")
        @Size(max = 512, message = "作废原因不能超过512个字符")
        String reason
) {
}
