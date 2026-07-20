package com.tuowei.erp.finance.receipt.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReceiptNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ReceiptNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextReceiptNo(LocalDate receiptDate) {
        return sequenceNumberGenerator.nextNumber("FIN_RECEIPT", "收款单", receiptDate);
    }
}
