package com.tuowei.erp.finance.aging.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.aging.web.FinanceAgingSummaryResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Compatibility facade for finance aging queries and response assembly. */
@Service
public class FinanceAgingService {

    private final FinanceAgingQueryService queryService;
    private final FinanceAgingAssemblyService assemblyService;

    @Autowired
    public FinanceAgingService(
            FinanceAgingQueryService queryService,
            FinanceAgingAssemblyService assemblyService
    ) {
        this.queryService = queryService;
        this.assemblyService = assemblyService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public FinanceAgingService(
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.queryService = new FinanceAgingQueryService(
                receivableMapper,
                payableMapper,
                customerMapper,
                supplierMapper,
                auditMetadataFactory
        );
        this.assemblyService = new FinanceAgingAssemblyService();
    }

    @Transactional(readOnly = true)
    public FinanceAgingSummaryResponse summary(LocalDate asOfDate) {
        return assemblyService.assemble(queryService.load(asOfDate));
    }
}
