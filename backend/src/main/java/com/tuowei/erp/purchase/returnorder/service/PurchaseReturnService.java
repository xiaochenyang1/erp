package com.tuowei.erp.purchase.returnorder.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnCreateRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class PurchaseReturnService {

    private final PurchaseReturnQueryService purchaseReturnQueryService;
    private final PurchaseReturnCommandService purchaseReturnCommandService;
    private final PurchaseReturnPostingService purchaseReturnPostingService;

    @org.springframework.beans.factory.annotation.Autowired
    public PurchaseReturnService(
            PurchaseReturnQueryService purchaseReturnQueryService,
            PurchaseReturnCommandService purchaseReturnCommandService,
            PurchaseReturnPostingService purchaseReturnPostingService
    ) {
        this.purchaseReturnQueryService = purchaseReturnQueryService;
        this.purchaseReturnCommandService = purchaseReturnCommandService;
        this.purchaseReturnPostingService = purchaseReturnPostingService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public PurchaseReturnService(PurchaseReturnMapper purchaseReturnMapper, PurchaseReturnLineMapper purchaseReturnLineMapper,
                                 PurchaseReceiptMapper purchaseReceiptMapper, PurchaseReceiptLineMapper purchaseReceiptLineMapper,
                                 ProductValidator productValidator,
                                 PurchaseReturnNumberService purchaseReturnNumberService,
                                 AuditMetadataFactory auditMetadataFactory,
                                 PurchaseReturnQueryService purchaseReturnQueryService,
                                 PurchaseReturnPostingService purchaseReturnPostingService) {
        this.purchaseReturnQueryService = purchaseReturnQueryService;
        this.purchaseReturnCommandService = new PurchaseReturnCommandService(
                purchaseReturnMapper, purchaseReturnLineMapper, purchaseReceiptMapper, purchaseReceiptLineMapper,
                productValidator, purchaseReturnNumberService, auditMetadataFactory, purchaseReturnQueryService
        );
        this.purchaseReturnPostingService = purchaseReturnPostingService;
    }

    @Transactional
    public PurchaseReturnResponse create(PurchaseReturnCreateRequest request) {
        return purchaseReturnCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReturnResponse> list(PurchaseReturnPageQuery query) {
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        return purchaseReturnQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportReturns(PurchaseReturnPageQuery query) {
        return purchaseReturnQueryService.exportReturns(query);
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse getById(Long id) {
        return purchaseReturnQueryService.getById(id);
    }

    @Transactional
    public PurchaseReturnResponse update(Long id, PurchaseReturnUpdateRequest request) {
        return purchaseReturnCommandService.update(id, request);
    }

    @Transactional
    public PurchaseReturnResponse cancel(Long id) {
        return purchaseReturnCommandService.cancel(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PurchaseReturnResponse post(Long id) {
        return purchaseReturnPostingService.post(id);
    }

}
