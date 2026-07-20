package com.tuowei.erp.purchase.receipt.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PurchaseReceiptNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public PurchaseReceiptNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextReceiptNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PURCHASE_RECEIPT", "采购入库单", bizDate);
    }
}
