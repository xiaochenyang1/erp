package com.tuowei.erp.production.issue.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.issue.mapper.ProductionIssueLineMapper;
import com.tuowei.erp.production.issue.mapper.ProductionIssueMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionIssueRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for production material issue commands. */
@Service
public class ProductionIssueService {
    private final ProductionIssueCommandService commandService;

    @Autowired
    public ProductionIssueService(ProductionIssueCommandService commandService) { this.commandService = commandService; }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionIssueService(ProductionOrderService productionOrderService, ProductionOrderMapper orderMapper,
                                  ProductionOrderMaterialMapper materialMapper, ProductionIssueMapper issueMapper,
                                  ProductionIssueLineMapper issueLineMapper, InventoryPostingService inventoryPostingService,
                                  InventorySerialNumberService inventorySerialNumberService, AccountPeriodGuard accountPeriodGuard,
                                  FinancePostingService financePostingService, AuditMetadataFactory auditMetadataFactory,
                                  SequenceNumberGenerator sequenceNumberGenerator) {
        this.commandService = new ProductionIssueCommandService(productionOrderService, orderMapper, materialMapper, issueMapper,
                issueLineMapper, inventoryPostingService, inventorySerialNumberService, accountPeriodGuard, financePostingService,
                auditMetadataFactory, sequenceNumberGenerator);
    }

    @Transactional
    public ProductionOrderResponse issue(Long orderId) { return commandService.issue(orderId); }

    @Transactional
    public ProductionOrderResponse issue(Long orderId, ProductionIssueRequest request) { return commandService.issue(orderId, request); }
}
