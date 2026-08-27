package com.tuowei.erp.finance.receipt.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for receipt queries and commands. */
@Service
public class ReceiptService {

    private final ReceiptQueryService queryService;
    private final ReceiptCommandService commandService;

    @Autowired
    public ReceiptService(ReceiptQueryService queryService, ReceiptCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public ReceiptService(
            ReceiptMapper receiptMapper,
            ReceiptAllocationMapper receiptAllocationMapper,
            ReceivableMapper receivableMapper,
            ReceiptNumberService receiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard
    ) {
        this.queryService = new ReceiptQueryService(receiptMapper, receiptAllocationMapper, auditMetadataFactory);
        this.commandService = new ReceiptCommandService(
                receiptMapper, receiptAllocationMapper, receivableMapper, receiptNumberService,
                auditMetadataFactory, accountPeriodGuard, queryService
        );
    }

    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest request) { return commandService.create(request); }

    @Transactional(readOnly = true)
    public ReceiptResponse detail(Long id) { return queryService.detail(id); }

    @Transactional
    public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) { return commandService.cancel(id, request); }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(ReceiptPageQuery query) {
        return queryService.list(query == null ? new ReceiptPageQuery() : query);
    }
}
