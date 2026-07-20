package com.tuowei.erp.sales.order.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SalesOrderNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public SalesOrderNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextOrderNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("SALES_ORDER", "销售订单", bizDate);
    }
}
