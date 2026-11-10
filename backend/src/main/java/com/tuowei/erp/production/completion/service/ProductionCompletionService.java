package com.tuowei.erp.production.completion.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionMapper;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for production completion commands. */
@Service
public class ProductionCompletionService {
    private final ProductionCompletionCommandService commandService;

    @Autowired
    public ProductionCompletionService(ProductionCompletionCommandService commandService) {
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionCompletionService(
            ProductionOrderService productionOrderService,
            ProductionOrderMapper orderMapper,
            ProductionCompletionMapper completionMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator,
            ProductionOperationService productionOperationService,
            QcInspectionGate qcInspectionGate
    ) {
        this.commandService = new ProductionCompletionCommandService(
                productionOrderService, orderMapper, completionMapper, inventoryPostingService,
                inventorySerialNumberService, accountPeriodGuard, financePostingService,
                auditMetadataFactory, sequenceNumberGenerator, productionOperationService, qcInspectionGate
        );
    }

    @Transactional
    public ProductionOrderResponse complete(Long orderId) { return commandService.complete(orderId); }

    @Transactional
    public ProductionOrderResponse complete(Long orderId, ProductionCompletionRequest request) {
        return commandService.complete(orderId, request);
    }
}
