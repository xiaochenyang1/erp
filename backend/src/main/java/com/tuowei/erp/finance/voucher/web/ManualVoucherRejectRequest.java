package com.tuowei.erp.finance.voucher.web;

import jakarta.validation.constraints.NotBlank;

public record ManualVoucherRejectRequest(
        @NotBlank(message = "驳回原因不能为空") String reason
) {
}
