package com.tuowei.erp.production.routing.web;

import java.util.List;

public record ProductionRoutingResponse(
        Long id,
        String routingCode,
        String routingName,
        Long bomId,
        String bomNo,
        Long productId,
        String status,
        String remark,
        List<ProductionRoutingOperationResponse> operations
) {
}
