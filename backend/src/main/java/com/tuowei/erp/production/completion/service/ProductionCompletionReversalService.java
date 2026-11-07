package com.tuowei.erp.production.completion.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionReversalMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionReversalRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for production completion reversal commands. */
@Service
public class ProductionCompletionReversalService {
    private final ProductionCompletionReversalCommandService commandService;

    @Autowired
    public ProductionCompletionReversalService(ProductionCompletionReversalCommandService commandService) {
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionCompletionReversalService(
            ProductionOrderService productionOrderService, ProductionOrderMapper orderMapper,
            ProductionCompletionReversalMapper reversalMapper, InventoryPostingService inventoryPostingService,
            AccountPeriodGuard accountPeriodGuard, FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory, SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.commandService = new ProductionCompletionReversalCommandService(
                productionOrderService, orderMapper, reversalMapper, inventoryPostingService,
                accountPeriodGuard, financePostingService, auditMetadataFactory, sequenceNumberGenerator
        );
    }

    @Transactional
    public ProductionOrderResponse reverseCompletion(Long orderId, ProductionCompletionReversalRequest request) {
        return commandService.reverseCompletion(orderId, request);
    }
}
