package com.tuowei.erp.purchase.inquiry.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PurchaseInquiryNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public PurchaseInquiryNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextInquiryNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PURCHASE_INQUIRY", "采购询价单", bizDate);
    }
}
