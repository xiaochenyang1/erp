package com.tuowei.erp.production.returnmaterial.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionReturnRequest;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnLineMapper;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnMapper;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for production material return commands. */
@Service
public class ProductionReturnService {
    private final ProductionReturnCommandService commandService;

    @Autowired
    public ProductionReturnService(ProductionReturnCommandService commandService) { this.commandService = commandService; }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ProductionReturnService(ProductionOrderService productionOrderService, ProductionOrderMapper orderMapper,
                                   ProductionOrderMaterialMapper materialMapper, ProductionReturnMapper returnMapper,
                                   ProductionReturnLineMapper returnLineMapper, InventoryPostingService inventoryPostingService,
                                   InventorySerialNumberService inventorySerialNumberService, AccountPeriodGuard accountPeriodGuard,
                                   FinancePostingService financePostingService, AuditMetadataFactory auditMetadataFactory,
                                   SequenceNumberGenerator sequenceNumberGenerator) {
        this.commandService = new ProductionReturnCommandService(productionOrderService, orderMapper, materialMapper, returnMapper,
                returnLineMapper, inventoryPostingService, inventorySerialNumberService, accountPeriodGuard, financePostingService,
                auditMetadataFactory, sequenceNumberGenerator);
    }

    @Transactional
    public ProductionOrderResponse returnMaterials(Long orderId, ProductionReturnRequest request) {
        return commandService.returnMaterials(orderId, request);
    }
}
