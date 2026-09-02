package com.tuowei.erp.inventory.adjust.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentPageQuery;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for inventory adjustment commands and queries. */
@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentQueryService adjustmentQueryService;
    private final InventoryAdjustmentCommandService adjustmentCommandService;

    @Autowired
    public InventoryAdjustmentService(
            InventoryAdjustmentQueryService adjustmentQueryService,
            InventoryAdjustmentCommandService adjustmentCommandService
    ) {
        this.adjustmentQueryService = adjustmentQueryService;
        this.adjustmentCommandService = adjustmentCommandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public InventoryAdjustmentService(
            InventoryAdjustmentMapper adjustmentMapper,
            InventoryAdjustmentLineMapper lineMapper,
            InventoryAdjustmentNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.adjustmentQueryService = new InventoryAdjustmentQueryService(
                adjustmentMapper,
                lineMapper,
                auditMetadataFactory
        );
        this.adjustmentCommandService = new InventoryAdjustmentCommandService(
                adjustmentMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                inventorySerialNumberService,
                financePostingService,
                auditMetadataFactory,
                warehouseMapper,
                productValidator,
                accountPeriodGuard,
                attachmentService
        );
    }

    @Transactional
    public InventoryAdjustmentResponse create(InventoryAdjustmentCreateRequest request) {
        return adjustmentCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryAdjustmentResponse> list(InventoryAdjustmentPageQuery query) {
        InventoryAdjustmentPageQuery safeQuery = query == null ? new InventoryAdjustmentPageQuery() : query;
        return adjustmentQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getById(Long id) {
        return adjustmentQueryService.getById(id);
    }

    @Transactional
    public InventoryAdjustmentResponse post(Long id) {
        return adjustmentCommandService.post(id);
    }

    @Transactional
    public InventoryAdjustmentResponse cancel(Long id) {
        return adjustmentCommandService.cancel(id);
    }
}
