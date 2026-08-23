package com.tuowei.erp.purchase.inquiry.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryUpdateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for purchase inquiry queries and commands. */
@Service
public class PurchaseInquiryService {
    private final PurchaseInquiryQueryService queryService;
    private final PurchaseInquiryCommandService commandService;

    public PurchaseInquiryService(PurchaseInquiryQueryService queryService, PurchaseInquiryCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public PurchaseInquiryResponse create(PurchaseInquiryCreateRequest request) { return commandService.create(request); }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseInquiryResponse> list(PurchaseInquiryPageQuery query) {
        return queryService.list(query == null ? new PurchaseInquiryPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryResponse getById(Long id) { return queryService.getById(id); }

    @Transactional
    public PurchaseInquiryResponse update(Long id, PurchaseInquiryUpdateRequest request) { return commandService.update(id, request); }

    @Transactional
    public PurchaseInquiryResponse submit(Long id) { return commandService.submit(id); }

    @Transactional
    public PurchaseInquiryResponse addQuote(Long id, PurchaseInquiryQuoteRequest request) { return commandService.addQuote(id, request); }

    @Transactional
    public PurchaseInquiryResponse selectQuote(Long id, PurchaseInquirySelectQuoteRequest request) { return commandService.selectQuote(id, request); }

    @Transactional(readOnly = true)
    public PurchaseInquiryPoPrefillResponse poPrefill(Long id) { return queryService.poPrefill(id); }

    @Transactional
    public PurchaseOrderResponse convertToPurchaseOrder(Long id) { return commandService.convertToPurchaseOrder(id); }

    @Transactional
    public PurchaseInquiryResponse cancel(Long id) { return commandService.cancel(id); }
}
