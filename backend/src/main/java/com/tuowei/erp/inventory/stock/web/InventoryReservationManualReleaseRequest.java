package com.tuowei.erp.inventory.stock.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InventoryReservationManualReleaseRequest(
        @NotNull(message = "释放数量不能为空")
        @DecimalMin(value = "0.0001", message = "释放数量必须大于0")
        BigDecimal qty,

        @NotBlank(message = "释放原因不能为空")
        @Size(max = 255, message = "释放原因不能超过255个字符")
        String reason
) {
}
