package com.tuowei.erp.finance.expense.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ExpenseNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ExpenseNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextExpenseNo(LocalDate expenseDate) {
        return sequenceNumberGenerator.nextNumber("FIN_EXPENSE", "费用单", expenseDate);
    }
}
