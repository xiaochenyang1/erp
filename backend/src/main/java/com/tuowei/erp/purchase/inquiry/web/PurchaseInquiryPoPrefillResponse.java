package com.tuowei.erp.purchase.inquiry.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 供前端调用已有采购订单创建接口的预填 DTO。
 * 字段与 {@code PurchaseOrderCreateRequest} 对齐。
 */
public record PurchaseInquiryPoPrefillResponse(
        Long inquiryId,
        String inquiryNo,
        Long supplierId,
        LocalDate orderDate,
        String remark,
        List<PurchaseInquiryPoPrefillLine> lines
) {
    public record PurchaseInquiryPoPrefillLine(
            Long productId,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal taxRate,
            String remark
    ) {
    }
}
