package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ContractNumberService {
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ContractNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextContractNo(LocalDate signedDate) {
        return sequenceNumberGenerator.nextNumber("COMMERCIAL_CONTRACT", "合同", signedDate);
    }
}
