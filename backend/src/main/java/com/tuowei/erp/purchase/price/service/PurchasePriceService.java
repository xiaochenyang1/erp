package com.tuowei.erp.purchase.price.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceCreateRequest;
import com.tuowei.erp.purchase.price.web.PurchasePricePageQuery;
import com.tuowei.erp.purchase.price.web.PurchasePriceResolveResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Compatibility facade for purchase price queries and commands. */
@Service
public class PurchasePriceService {

    private final PurchasePriceQueryService queryService;
    private final PurchasePriceCommandService commandService;

    public PurchasePriceService(PurchasePriceQueryService queryService, PurchasePriceCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public PurchasePriceResponse create(PurchasePriceCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional
    public PurchasePriceResponse update(Long id, PurchasePriceUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public PurchasePriceResponse enable(Long id) {
        return commandService.enable(id);
    }

    @Transactional
    public PurchasePriceResponse disable(Long id) {
        return commandService.disable(id);
    }

    @Transactional(readOnly = true)
    public PurchasePriceResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchasePriceResponse> list(PurchasePricePageQuery query) {
        PurchasePricePageQuery safeQuery = query == null ? new PurchasePricePageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public PurchasePriceResolveResponse resolve(Long supplierId, Long productId, LocalDate bizDate) {
        return queryService.resolve(supplierId, productId, bizDate);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveMaxPrice(Long companyId, Long accountBookId, Long supplierId, Long productId, LocalDate bizDate) {
        return queryService.resolveMaxPrice(companyId, accountBookId, supplierId, productId, bizDate);
    }
}
