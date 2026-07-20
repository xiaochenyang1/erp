package com.tuowei.erp.production.bom.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProductionBomCreateRequest(
        @NotNull Long productId,
        @NotNull BigDecimal baseQty,
        String remark,
        @NotEmpty List<@Valid ProductionBomLineRequest> lines
) {
}
