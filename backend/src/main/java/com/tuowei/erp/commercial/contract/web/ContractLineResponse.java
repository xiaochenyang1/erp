package com.tuowei.erp.commercial.contract.web;

import java.math.BigDecimal;

public record ContractLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        String productCode,
        String productName,
        BigDecimal quantity,
        BigDecimal committedQuantity,
        BigDecimal fulfilledQuantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String remark
) {
    public ContractLineResponse(Long id, Integer lineNo, Long productId, String productCode, String productName,
                                BigDecimal quantity, BigDecimal fulfilledQuantity, BigDecimal unitPrice,
                                BigDecimal amount, String remark) {
        this(id, lineNo, productId, productCode, productName, quantity, BigDecimal.ZERO,
                fulfilledQuantity, unitPrice, amount, remark);
    }
}
