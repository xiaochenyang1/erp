package com.tuowei.erp.sales.quote.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SalesQuoteNumberService {
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public SalesQuoteNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextQuoteNo(LocalDate date, AuditMetadata audit) {
        return sequenceNumberGenerator.nextNumber("SALES_QUOTE", "销售报价", date);
    }
}
