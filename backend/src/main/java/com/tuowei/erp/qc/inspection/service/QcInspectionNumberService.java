package com.tuowei.erp.qc.inspection.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class QcInspectionNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public QcInspectionNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextInspectionNo(LocalDate bizDate) {
        return sequenceNumberGenerator.nextNumber("QC_INSPECTION", "来料检验单", bizDate);
    }
}
