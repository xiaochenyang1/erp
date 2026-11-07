package com.tuowei.erp.purchase.receipt.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class PurchaseReceiptService {

    private final PurchaseReceiptQueryService purchaseReceiptQueryService;
    private final PurchaseReceiptCommandService purchaseReceiptCommandService;
    private final PurchaseReceiptPostingService purchaseReceiptPostingService;

    @org.springframework.beans.factory.annotation.Autowired
    public PurchaseReceiptService(
            PurchaseReceiptQueryService purchaseReceiptQueryService,
            PurchaseReceiptCommandService purchaseReceiptCommandService,
            PurchaseReceiptPostingService purchaseReceiptPostingService
    ) {
        this.purchaseReceiptQueryService = purchaseReceiptQueryService;
        this.purchaseReceiptCommandService = purchaseReceiptCommandService;
        this.purchaseReceiptPostingService = purchaseReceiptPostingService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public PurchaseReceiptService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            WarehouseMapper warehouseMapper,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseReceiptNumberService purchaseReceiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseReceiptQueryService purchaseReceiptQueryService,
            PurchaseReceiptPostingService purchaseReceiptPostingService
    ) {
        this.purchaseReceiptQueryService = purchaseReceiptQueryService;
        this.purchaseReceiptCommandService = new PurchaseReceiptCommandService(
                purchaseReceiptMapper, purchaseReceiptLineMapper, warehouseMapper, purchaseOrderLookupService,
                purchaseReceiptNumberService, auditMetadataFactory, purchaseReceiptQueryService
        );
        this.purchaseReceiptPostingService = purchaseReceiptPostingService;
    }

    @Transactional
    public PurchaseReceiptResponse create(PurchaseReceiptCreateRequest request) {
        return purchaseReceiptCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReceiptResponse> list(PurchaseReceiptPageQuery query) {
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        return purchaseReceiptQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportReceipts(PurchaseReceiptPageQuery query) {
        return purchaseReceiptQueryService.exportReceipts(query);
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getById(Long id) {
        return purchaseReceiptQueryService.getById(id);
    }

    @Transactional
    public PurchaseReceiptResponse update(Long id, PurchaseReceiptUpdateRequest request) {
        return purchaseReceiptCommandService.update(id, request);
    }

    @Transactional
    public PurchaseReceiptResponse cancel(Long id) {
        return purchaseReceiptCommandService.cancel(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PurchaseReceiptResponse post(Long id) {
        return purchaseReceiptPostingService.post(id);
    }

}
