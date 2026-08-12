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

/**
 * Thin facade over the read ({@link PurchaseInquiryQueryService}) and write
 * ({@link PurchaseInquiryPostingService}) sides of purchase inquiry handling. Quote persistence is
 * delegated to {@link PurchaseInquiryQuoteService}. The facade only owns transaction boundaries;
 * all logic lives in the collaborators.
 */
@Service
public class PurchaseInquiryService {

    private final PurchaseInquiryQueryService purchaseInquiryQueryService;
    private final PurchaseInquiryPostingService purchaseInquiryPostingService;

    public PurchaseInquiryService(
            PurchaseInquiryQueryService purchaseInquiryQueryService,
            PurchaseInquiryPostingService purchaseInquiryPostingService
    ) {
        this.purchaseInquiryQueryService = purchaseInquiryQueryService;
        this.purchaseInquiryPostingService = purchaseInquiryPostingService;
    }

    @Transactional
    public PurchaseInquiryResponse create(PurchaseInquiryCreateRequest request) {
        return purchaseInquiryPostingService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseInquiryResponse> list(PurchaseInquiryPageQuery query) {
        PurchaseInquiryPageQuery safeQuery = query == null ? new PurchaseInquiryPageQuery() : query;
        return purchaseInquiryQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryResponse getById(Long id) {
        return purchaseInquiryQueryService.getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse update(Long id, PurchaseInquiryUpdateRequest request) {
        return purchaseInquiryPostingService.update(id, request);
    }

    @Transactional
    public PurchaseInquiryResponse submit(Long id) {
        return purchaseInquiryPostingService.submit(id);
    }

    @Transactional
    public PurchaseInquiryResponse addQuote(Long id, PurchaseInquiryQuoteRequest request) {
        return purchaseInquiryPostingService.addQuote(id, request);
    }

    @Transactional
    public PurchaseInquiryResponse selectQuote(Long id, PurchaseInquirySelectQuoteRequest request) {
        return purchaseInquiryPostingService.selectQuote(id, request);
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryPoPrefillResponse poPrefill(Long id) {
        return purchaseInquiryQueryService.poPrefill(id);
    }

    @Transactional
    public PurchaseOrderResponse convertToPurchaseOrder(Long id) {
        return purchaseInquiryPostingService.convertToPurchaseOrder(id);
    }

    @Transactional
    public PurchaseInquiryResponse cancel(Long id) {
        return purchaseInquiryPostingService.cancel(id);
    }
}
