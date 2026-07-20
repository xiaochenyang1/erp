package com.tuowei.erp.purchase.returnorder.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PurchaseReturnNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public PurchaseReturnNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextReturnNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PURCHASE_RETURN", "采购退货单", bizDate);
    }
}
