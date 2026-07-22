package com.tuowei.erp.masterdata.customer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CustomerUpdateRequest(
        @NotBlank(message = "customerName不能为空") String customerName,
        @NotBlank(message = "customerType不能为空") String customerType,
        String contactName,
        String contactPhone,
        String email,
        @NotBlank(message = "settlementMethod不能为空") String settlementMethod,
        @NotNull(message = "creditLimit不能为空") BigDecimal creditLimit,
        String address,
        String status,
        String remark
) {
}
