package com.tuowei.erp.finance.invoice.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoicePageQuery;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import com.tuowei.erp.finance.invoice.web.InvoiceUpdateRequest;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for invoice-register queries and commands. */
@Service
public class FinanceInvoiceService {

    private final FinanceInvoiceQueryService queryService;
    private final FinanceInvoiceCommandService commandService;

    @Autowired
    public FinanceInvoiceService(FinanceInvoiceQueryService queryService, FinanceInvoiceCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public FinanceInvoiceService(
            InvoiceRegisterMapper invoiceRegisterMapper,
            InvoiceNumberService invoiceNumberService,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentService attachmentService
    ) {
        this.queryService = new FinanceInvoiceQueryService(invoiceRegisterMapper, auditMetadataFactory);
        this.commandService = new FinanceInvoiceCommandService(
                invoiceRegisterMapper, invoiceNumberService, purchaseOrderMapper, salesOrderMapper,
                auditMetadataFactory, attachmentService, queryService
        );
    }

    @Transactional
    public InvoiceResponse create(InvoiceCreateRequest request) { return commandService.create(request); }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(InvoicePageQuery query) {
        return queryService.list(query == null ? new InvoicePageQuery() : query);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse detail(Long id) { return queryService.detail(id); }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceUpdateRequest request) { return commandService.update(id, request); }

    @Transactional
    public InvoiceResponse post(Long id) { return commandService.post(id); }

    @Transactional
    public InvoiceResponse cancel(Long id) { return commandService.cancel(id); }
}
