package com.tuowei.erp.purchase.requisition.web;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record PurchaseRequisitionLineRequest(@NotNull Long productId, @NotNull @DecimalMin("0.0001") BigDecimal qty, String remark) {}
