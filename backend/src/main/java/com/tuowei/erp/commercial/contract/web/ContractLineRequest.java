package com.tuowei.erp.commercial.contract.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContractLineRequest(
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "quantity不能为空") @DecimalMin(value = "0.0001", message = "quantity必须大于0") BigDecimal quantity,
        @NotNull(message = "unitPrice不能为空") @DecimalMin(value = "0.00", message = "unitPrice不能为负") BigDecimal unitPrice,
        String remark
) {
}
