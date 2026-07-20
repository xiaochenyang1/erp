package com.tuowei.erp.sales.returnorder.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SalesReturnNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public SalesReturnNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextReturnNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("SALES_RETURN", "销售退货单", bizDate);
    }
}
