package com.tuowei.erp.inventory.serial.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.web.InventorySerialCreateRequest;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.inventory.serial.web.InventorySerialResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventorySerialNumberService {

    private final InventorySerialNumberQueryService queryService;
    private final InventorySerialNumberCommandService commandService;

    @Autowired
    public InventorySerialNumberService(
            InventorySerialNumberQueryService queryService,
            InventorySerialNumberCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public InventorySerialNumberService(
            InventorySerialNumberMapper serialNumberMapper,
            ProductMapper productMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.queryService = new InventorySerialNumberQueryService(
                serialNumberMapper, productMapper, auditMetadataFactory
        );
        this.commandService = new InventorySerialNumberCommandService(
                serialNumberMapper, productMapper, auditMetadataFactory, queryService
        );
    }

    @Transactional
    public InventorySerialResponse create(InventorySerialCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional
    public InventorySerialResponse issue(Long id, String outboundBizType, String outboundBizNo) {
        return commandService.issue(id, outboundBizType, outboundBizNo);
    }

    @Transactional
    public InventorySerialResponse scrap(Long id) {
        return commandService.scrap(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventorySerialResponse> list(InventorySerialPageQuery query) {
        return queryService.list(query == null ? new InventorySerialPageQuery() : query);
    }


    @Transactional
    public void registerInboundSerials(
            Long productId,
            Long warehouseId,
            Long locationId,
            String serialNos,
            String inboundBizType,
            String inboundBizNo,
            java.math.BigDecimal qty,
            AuditMetadata audit
    ) {
        commandService.registerInboundSerials(productId, warehouseId, locationId, serialNos,
                inboundBizType, inboundBizNo, qty, audit);
    }

    @Transactional
    public void issueOutboundSerials(
            Long productId,
            String serialNos,
            String outboundBizType,
            String outboundBizNo,
            java.math.BigDecimal qty,
            AuditMetadata audit
    ) {
        commandService.issueOutboundSerials(productId, serialNos, outboundBizType, outboundBizNo, qty, audit);
    }

    @Transactional
    public void moveInStockSerials(
            Long productId,
            Long toWarehouseId,
            Long toLocationId,
            String serialNos,
            java.math.BigDecimal qty,
            AuditMetadata audit
    ) {
        commandService.moveInStockSerials(productId, toWarehouseId, toLocationId, serialNos, qty, audit);
    }
}
