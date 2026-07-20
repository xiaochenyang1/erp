package com.tuowei.erp.production.bom.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ProductionBomNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionBomNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextBomNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("PRODUCTION_BOM", "BOM", bizDate);
    }
}
