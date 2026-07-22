package com.tuowei.erp.masterdata.supplier.web;

import java.math.BigDecimal;

public record SupplierPayableExposureResponse(
        Long supplierId,
        BigDecimal outstandingPayable,
        BigDecimal openPurchaseOrderAmount,
        BigDecimal totalExposure
) {
}
