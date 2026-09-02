package com.tuowei.erp.finance.payment.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for payment queries and commands. */
@Service
public class PaymentService {
    private final PaymentQueryService queryService;
    private final PaymentCommandService commandService;

    @Autowired
    public PaymentService(PaymentQueryService queryService, PaymentCommandService commandService) { this.queryService = queryService; this.commandService = commandService; }
    /** Keeps direct construction in existing non-Spring tests compatible. */
    public PaymentService(PaymentMapper paymentMapper, PaymentAllocationMapper paymentAllocationMapper, PayableMapper payableMapper, PaymentNumberService paymentNumberService, AuditMetadataFactory auditMetadataFactory, AccountPeriodGuard accountPeriodGuard) {
        this.queryService = new PaymentQueryService(paymentMapper, paymentAllocationMapper, auditMetadataFactory);
        this.commandService = new PaymentCommandService(paymentMapper, paymentAllocationMapper, payableMapper, paymentNumberService, auditMetadataFactory, accountPeriodGuard, queryService);
    }
    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) { return commandService.create(request); }
    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) { return queryService.detail(id); }
    @Transactional
    public PaymentResponse cancel(Long id, PaymentCancelRequest request) { return commandService.cancel(id, request); }
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentPageQuery query) { return queryService.list(query == null ? new PaymentPageQuery() : query); }
}
