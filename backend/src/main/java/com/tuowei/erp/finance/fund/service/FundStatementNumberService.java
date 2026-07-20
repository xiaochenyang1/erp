package com.tuowei.erp.finance.fund.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class FundStatementNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public FundStatementNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextStatementNo(LocalDate transactionDate) {
        return sequenceNumberGenerator.nextNumber("FIN_BANK_STATEMENT", "银行流水", transactionDate);
    }
}
