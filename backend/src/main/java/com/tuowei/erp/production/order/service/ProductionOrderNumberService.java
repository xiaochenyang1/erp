package com.tuowei.erp.production.order.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ProductionOrderNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionOrderNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextOrderNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PRODUCTION_ORDER", "生产工单", bizDate);
    }
}
