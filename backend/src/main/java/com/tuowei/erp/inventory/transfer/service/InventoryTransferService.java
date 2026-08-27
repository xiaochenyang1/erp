package com.tuowei.erp.inventory.transfer.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferLineMapper;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferMapper;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferPageQuery;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for inventory transfer commands and queries. */
@Service
public class InventoryTransferService {

    // Data-scope list assembly remains backed by the shared scopedUserResolver.resolve path in the query collaborator.

    private final InventoryTransferQueryService transferQueryService;
    private final InventoryTransferCommandService transferCommandService;

    @Autowired
    public InventoryTransferService(
            InventoryTransferQueryService transferQueryService,
            InventoryTransferCommandService transferCommandService
    ) {
        this.transferQueryService = transferQueryService;
        this.transferCommandService = transferCommandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public InventoryTransferService(
            InventoryTransferMapper transferMapper,
            InventoryTransferLineMapper lineMapper,
            InventoryTransferNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.transferQueryService = new InventoryTransferQueryService(
                transferMapper,
                lineMapper,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
        this.transferCommandService = new InventoryTransferCommandService(
                transferMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                inventorySerialNumberService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                userMapper,
                warehouseMapper,
                productValidator,
                accountPeriodGuard,
                attachmentService
        );
    }

    @Transactional
    public InventoryTransferResponse create(InventoryTransferCreateRequest request) {
        return transferCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransferResponse> list(InventoryTransferPageQuery query) {
        InventoryTransferPageQuery safeQuery = query == null ? new InventoryTransferPageQuery() : query;
        return transferQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public InventoryTransferResponse getById(Long id) {
        return transferQueryService.getById(id);
    }

    @Transactional
    public InventoryTransferResponse post(Long id) {
        return transferCommandService.post(id);
    }

    @Transactional
    public InventoryTransferResponse cancel(Long id) {
        return transferCommandService.cancel(id);
    }
}
