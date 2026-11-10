package com.tuowei.erp.commercial.contract.web;

import java.math.BigDecimal;

public record ContractVersionLineResponse(
        Integer lineNo,
        Long productId,
        BigDecimal quantity,
        BigDecimal fulfilledQuantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String remark
) {}
