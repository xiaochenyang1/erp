package com.tuowei.erp.production.operation.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.web.ProductionOperationReportRequest;
import com.tuowei.erp.production.operation.web.ProductionOrderOperationResponse;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Compatibility facade for production operation queries and commands. */
@Service
public class ProductionOperationService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";

    private final ProductionOperationQueryService queryService;
    private final ProductionOperationCommandService commandService;

    @Autowired
    public ProductionOperationService(
            ProductionOperationQueryService queryService,
            ProductionOperationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionOperationService(
            ProductionOrderOperationMapper operationMapper,
            ProductionOrderMapper orderMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.queryService = new ProductionOperationQueryService(
                operationMapper, orderMapper, workCenterMapper, auditMetadataFactory
        );
        this.commandService = new ProductionOperationCommandService(
                operationMapper, routingMapper, routingOperationMapper, auditMetadataFactory, queryService
        );
    }

    @Transactional
    public void generateForReleasedOrder(ProductionOrderEntity order, AuditMetadata audit) {
        commandService.generateForReleasedOrder(order, audit);
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderOperationResponse> listByOrder(Long orderId) {
        return queryService.listByOrder(orderId);
    }

    @Transactional
    public ProductionOrderOperationResponse report(
            Long orderId,
            Long operationId,
            ProductionOperationReportRequest request
    ) {
        return commandService.report(orderId, operationId, request);
    }

    @Transactional(readOnly = true)
    public void assertReadyForCompletion(ProductionOrderEntity order, BigDecimal completionQty) {
        queryService.assertReadyForCompletion(order, completionQty);
    }
}
