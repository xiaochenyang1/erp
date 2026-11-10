package com.tuowei.erp.production.cost.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductionCostPeriodCloseBatchRequest(
        @NotEmpty(message = "请选择待关单工单")
        @Size(max = 100, message = "单次最多批量关单 100 张工单")
        List<@NotNull Long> orderIds,
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
