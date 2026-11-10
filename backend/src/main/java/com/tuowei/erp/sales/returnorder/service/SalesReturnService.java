package com.tuowei.erp.sales.returnorder.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade delegating sales-return reads to the query service, drafts/edits to the command service and posting to the posting service. */
@Service
public class SalesReturnService {

    private final SalesReturnQueryService salesReturnQueryService;
    private final SalesReturnPostingService salesReturnPostingService;
    private final SalesReturnCommandService salesReturnCommandService;

    public SalesReturnService(
            SalesReturnQueryService salesReturnQueryService,
            SalesReturnPostingService salesReturnPostingService,
            SalesReturnCommandService salesReturnCommandService
    ) {
        this.salesReturnQueryService = salesReturnQueryService;
        this.salesReturnPostingService = salesReturnPostingService;
        this.salesReturnCommandService = salesReturnCommandService;
    }

    @Transactional
    public SalesReturnResponse create(SalesReturnCreateRequest request) {
        return salesReturnCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesReturnResponse> list(SalesReturnPageQuery query) {
        SalesReturnPageQuery safeQuery = query == null ? new SalesReturnPageQuery() : query;
        return salesReturnQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse getById(Long id) {
        return salesReturnQueryService.getById(id);
    }

    @Transactional
    public SalesReturnResponse update(Long id, SalesReturnUpdateRequest request) {
        return salesReturnCommandService.update(id, request);
    }

    @Transactional
    public SalesReturnResponse cancel(Long id) {
        return salesReturnCommandService.cancel(id);
    }

    @Transactional
    public SalesReturnResponse post(Long id) {
        return salesReturnPostingService.post(id);
    }
}
