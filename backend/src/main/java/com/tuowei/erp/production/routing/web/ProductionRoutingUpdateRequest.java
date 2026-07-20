package com.tuowei.erp.production.routing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProductionRoutingUpdateRequest(
        @NotBlank(message = "工艺路线名称不能为空") String routingName,
        String remark,
        @NotEmpty(message = "工艺路线至少需要一道工序")
        List<@NotNull(message = "工序不能为空") @Valid ProductionRoutingOperationRequest> operations
) {
}
