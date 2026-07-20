package com.tuowei.erp.production.routing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductionRoutingOperationRequest(
        @NotBlank(message = "工序编码不能为空") String operationCode,
        @NotBlank(message = "工序名称不能为空") String operationName,
        @NotNull(message = "工作中心不能为空") Long workCenterId,
        @NotNull(message = "标准工时必须大于0")
        @DecimalMin(value = "0.01", message = "标准工时必须大于0") BigDecimal standardMinutes,
        String remark
) {
}
