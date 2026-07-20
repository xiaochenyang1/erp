package com.tuowei.erp.finance.invoice.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InvoiceNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public InvoiceNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextInvoiceNo(LocalDate invoiceDate) {
        return sequenceNumberGenerator.nextNumber("FIN_INVOICE", "发票登记", invoiceDate);
    }
}
