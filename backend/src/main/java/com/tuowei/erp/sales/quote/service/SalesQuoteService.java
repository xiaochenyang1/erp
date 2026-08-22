package com.tuowei.erp.sales.quote.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.quote.web.SalesQuotePageQuery;
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for sales quote queries and commands. */
@Service
public class SalesQuoteService {
    private final SalesQuoteQueryService queryService;
    private final SalesQuoteCommandService commandService;

    public SalesQuoteService(SalesQuoteQueryService queryService, SalesQuoteCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public SalesQuoteResponse create(SalesQuoteSaveRequest request) { return commandService.create(request); }

    @Transactional
    public SalesQuoteResponse update(Long id, SalesQuoteSaveRequest request) { return commandService.update(id, request); }

    @Transactional(readOnly = true)
    public SalesQuoteResponse detail(Long id) { return queryService.detail(id); }

    @Transactional(readOnly = true)
    public PageResponse<SalesQuoteResponse> list(SalesQuotePageQuery query) {
        return queryService.list(query == null ? new SalesQuotePageQuery() : query);
    }

    @Transactional
    public SalesQuoteResponse confirm(Long id) { return commandService.confirm(id); }

    @Transactional
    public SalesQuoteResponse cancel(Long id) { return commandService.cancel(id); }

    @Transactional
    public SalesOrderResponse convertToOrder(Long id, Long warehouseId) { return commandService.convertToOrder(id, warehouseId); }
}
