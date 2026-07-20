package com.tuowei.erp.sales.delivery.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SalesDeliveryNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public SalesDeliveryNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextDeliveryNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("SALES_DELIVERY", "销售出库单", bizDate);
    }
}
