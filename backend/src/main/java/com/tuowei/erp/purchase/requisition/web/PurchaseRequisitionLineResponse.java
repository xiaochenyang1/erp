package com.tuowei.erp.purchase.requisition.web;
import java.math.BigDecimal;
public record PurchaseRequisitionLineResponse(Long id, Integer lineNo, Long productId, String productCode, String productName, BigDecimal qty, String remark) {}
