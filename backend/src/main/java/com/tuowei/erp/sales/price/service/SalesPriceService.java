package com.tuowei.erp.sales.price.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.price.web.SalesPriceCreateRequest;
import com.tuowei.erp.sales.price.web.SalesPricePageQuery;
import com.tuowei.erp.sales.price.web.SalesPriceResolveResponse;
import com.tuowei.erp.sales.price.web.SalesPriceResponse;
import com.tuowei.erp.sales.price.web.SalesPriceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Compatibility facade for sales price queries and commands. */
@Service
public class SalesPriceService {

    private final SalesPriceQueryService queryService;
    private final SalesPriceCommandService commandService;

    public SalesPriceService(SalesPriceQueryService queryService, SalesPriceCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public SalesPriceResponse create(SalesPriceCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional
    public SalesPriceResponse update(Long id, SalesPriceUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public SalesPriceResponse enable(Long id) {
        return commandService.enable(id);
    }

    @Transactional
    public SalesPriceResponse disable(Long id) {
        return commandService.disable(id);
    }

    @Transactional(readOnly = true)
    public SalesPriceResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesPriceResponse> list(SalesPricePageQuery query) {
        SalesPricePageQuery safeQuery = query == null ? new SalesPricePageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public SalesPriceResolveResponse resolve(Long customerId, Long productId, LocalDate bizDate) {
        return queryService.resolve(customerId, productId, bizDate);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveMinPrice(Long companyId, Long accountBookId, Long customerId, Long productId, LocalDate bizDate) {
        return queryService.resolveMinPrice(companyId, accountBookId, customerId, productId, bizDate);
    }
}
