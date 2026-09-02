package com.tuowei.erp.inventory.check.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckCreateRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckPageQuery;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for inventory stock-check commands and queries. */
@Service
public class InventoryStockCheckService {

    private final InventoryStockCheckQueryService checkQueryService;
    private final InventoryStockCheckCommandService checkCommandService;

    @Autowired
    public InventoryStockCheckService(
            InventoryStockCheckQueryService checkQueryService,
            InventoryStockCheckCommandService checkCommandService
    ) {
        this.checkQueryService = checkQueryService;
        this.checkCommandService = checkCommandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public InventoryStockCheckService(
            InventoryStockCheckMapper checkMapper,
            InventoryStockCheckLineMapper lineMapper,
            InventoryStockCheckNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventoryAdjustmentService adjustmentService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.checkQueryService = new InventoryStockCheckQueryService(checkMapper, lineMapper, auditMetadataFactory);
        this.checkCommandService = new InventoryStockCheckCommandService(
                checkMapper, lineMapper, numberService, inventoryPostingService, adjustmentService,
                auditMetadataFactory, warehouseMapper, productValidator, accountPeriodGuard, attachmentService
        );
    }

    @Transactional
    public InventoryStockCheckResponse create(InventoryStockCheckCreateRequest request) {
        return checkCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryStockCheckResponse> list(InventoryStockCheckPageQuery query) {
        return checkQueryService.list(query == null ? new InventoryStockCheckPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public InventoryStockCheckResponse getById(Long id) {
        return checkQueryService.getById(id);
    }

    @Transactional
    public InventoryStockCheckResponse postAdjustment(Long id) {
        return checkCommandService.postAdjustment(id);
    }

    @Transactional
    public InventoryStockCheckResponse update(Long id, InventoryStockCheckUpdateRequest request) {
        return checkCommandService.update(id, request);
    }

    @Transactional
    public InventoryStockCheckResponse cancel(Long id) {
        return checkCommandService.cancel(id);
    }
}
