package com.tuowei.erp.purchase.order.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PurchaseOrderNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public PurchaseOrderNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextOrderNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PURCHASE_ORDER", "采购订单", bizDate);
    }
}
