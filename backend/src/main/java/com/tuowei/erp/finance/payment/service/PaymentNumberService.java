package com.tuowei.erp.finance.payment.service;

import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PaymentNumberService {

    private final SequenceNumberGenerator sequenceNumberGenerator;

    public PaymentNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    public String nextPaymentNo(LocalDate paymentDate) {
        return sequenceNumberGenerator.nextNumber("FIN_PAYMENT", "付款单", paymentDate);
    }
}
