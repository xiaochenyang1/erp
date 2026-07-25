package com.tuowei.erp.purchase.requisition.web;
import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import java.time.LocalDate; import java.util.List;
public record PurchaseRequisitionCreateRequest(
    @NotNull LocalDate requisitionDate, LocalDate neededDate, Long supplierId, String remark,
    @Valid @NotEmpty List<PurchaseRequisitionLineRequest> lines
) {}
