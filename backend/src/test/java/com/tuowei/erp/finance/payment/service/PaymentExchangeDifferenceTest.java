package com.tuowei.erp.finance.payment.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.finance.posting.SettlementPostingAmounts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 付款核销的已实现汇兑损益：结算汇率高于应付入账汇率时形成汇兑损失，作废按落库快照精确冲回。
 */
class PaymentExchangeDifferenceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            902L,
            11L,
            22L,
            LocalDateTime.of(2026, 9, 18, 11, 0)
    );
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 9, 18);
    private static final Long SUPPLIER_ID = 7101L;

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final PaymentAllocationMapper paymentAllocationMapper = mock(PaymentAllocationMapper.class);
    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final PaymentQueryService queryService = mock(PaymentQueryService.class);
    private final FinancePostingService postingService = mock(FinancePostingService.class);
    private final SettlementCurrencyService settlementCurrencyService = mock(SettlementCurrencyService.class);
    private final PaymentCommandService service = new PaymentCommandService(
            paymentMapper,
            paymentAllocationMapper,
            payableMapper,
            paymentNumberService(),
            auditMetadataFactory(),
            mock(AccountPeriodGuard.class),
            queryService,
            postingService,
            settlementCurrencyService
    );

    @Test
    void payingAboveTheBookingRateRecordsARealisedExchangeLoss() {
        resolveSettlementCurrency("EUR", "8.10");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(assignPaymentId(5101L));
        when(payableMapper.selectById(6101L)).thenReturn(payable(6101L, "EUR", "8.00", "500.00", "0.00"));
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);
        when(paymentAllocationMapper.insert(any(PaymentAllocationEntity.class))).thenReturn(1);

        service.create(new PaymentCreateRequest(
                SUPPLIER_ID,
                SETTLEMENT_DATE,
                new BigDecimal("500.00"),
                "EUR",
                new BigDecimal("8.10"),
                null,
                List.of(new PaymentAllocationRequest(6101L, new BigDecimal("500.00")))
        ));

        ArgumentCaptor<PaymentAllocationEntity> allocationCaptor = ArgumentCaptor.forClass(PaymentAllocationEntity.class);
        verify(paymentAllocationMapper).insert(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getBaseAmount()).isEqualByComparingTo("4050.000000");
        assertThat(allocationCaptor.getValue().getBaseSettledAmount()).isEqualByComparingTo("4000.000000");
        assertThat(allocationCaptor.getValue().getFxGainLossAmount()).isEqualByComparingTo("50.000000");

        PayableEntity payable = capturePayable();
        assertThat(payable.getSettledAmount()).isEqualByComparingTo("500.00");
        assertThat(payable.getBaseSettledAmount()).isEqualByComparingTo("4000.000000");
        assertThat(payable.getStatus()).isEqualTo("SETTLED");

        SettlementPostingAmounts amounts = capturePosting();
        assertThat(amounts.reliefAmount()).isEqualByComparingTo("4000.00");
        assertThat(amounts.advanceAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.fxAmount()).isEqualByComparingTo("50.00");
        assertThat(amounts.cashAmount()).isEqualByComparingTo("4050.00");
    }

    @Test
    void payingAPayableInAnotherCurrencyIsRejected() {
        resolveSettlementCurrency("USD", "7.30");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(assignPaymentId(5102L));
        when(payableMapper.selectById(6102L)).thenReturn(payable(6102L, "CNY", "1", "1000.00", "0.00"));

        assertThatThrownBy(() -> service.create(new PaymentCreateRequest(
                SUPPLIER_ID,
                SETTLEMENT_DATE,
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("7.30"),
                null,
                List.of(new PaymentAllocationRequest(6102L, new BigDecimal("1000.00")))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("付款币种与应付币种不一致");
    }

    @Test
    void cancellationReversesTheStoredExchangeDifferenceExactly() {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(5103L);
        payment.setCompanyId(AUDIT.companyId());
        payment.setAccountBookId(AUDIT.accountBookId());
        payment.setPaymentNo("FP-5103");
        payment.setPaymentDate(SETTLEMENT_DATE);
        payment.setSupplierId(SUPPLIER_ID);
        payment.setStatus("POSTED");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setAllocatedAmount(new BigDecimal("500.00"));
        payment.setCurrencyCode("EUR");
        payment.setExchangeRate(new BigDecimal("8.10"));
        when(queryService.requirePayment(5103L)).thenReturn(payment);
        when(paymentMapper.updateById(any(PaymentEntity.class))).thenReturn(1);

        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setId(8101L);
        allocation.setPayableId(6103L);
        allocation.setAmount(new BigDecimal("500.00"));
        allocation.setBaseAmount(new BigDecimal("4050.000000"));
        allocation.setBaseSettledAmount(new BigDecimal("4000.000000"));
        allocation.setFxGainLossAmount(new BigDecimal("50.000000"));
        when(queryService.allocations(payment)).thenReturn(List.of(allocation));
        when(payableMapper.selectById(6103L)).thenReturn(payable(6103L, "EUR", "8.00", "500.00", "500.00"));
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);

        service.cancel(5103L, new PaymentCancelRequest("付错供应商"));

        ArgumentCaptor<SettlementPostingAmounts> amountsCaptor = ArgumentCaptor.forClass(SettlementPostingAmounts.class);
        verify(postingService).recordPaymentCancellation(eq(payment), amountsCaptor.capture(), eq(AUDIT));
        assertThat(amountsCaptor.getValue().reliefAmount()).isEqualByComparingTo("4000.00");
        assertThat(amountsCaptor.getValue().fxAmount()).isEqualByComparingTo("50.00");
        assertThat(amountsCaptor.getValue().cashAmount()).isEqualByComparingTo("4050.00");

        PayableEntity payable = capturePayable();
        assertThat(payable.getSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(payable.getBaseSettledAmount()).isEqualByComparingTo("0.000000");
        assertThat(payable.getStatus()).isEqualTo("UNSETTLED");
    }

    private void resolveSettlementCurrency(String currencyCode, String rate) {
        when(settlementCurrencyService.resolve(any(), any(), any(), any()))
                .thenReturn(new SettlementCurrencyService.Resolution(currencyCode, new BigDecimal(rate)));
    }

    private PayableEntity capturePayable() {
        ArgumentCaptor<PayableEntity> captor = ArgumentCaptor.forClass(PayableEntity.class);
        verify(payableMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private SettlementPostingAmounts capturePosting() {
        ArgumentCaptor<SettlementPostingAmounts> captor = ArgumentCaptor.forClass(SettlementPostingAmounts.class);
        verify(postingService).recordPayment(any(PaymentEntity.class), captor.capture(), eq(AUDIT));
        return captor.getValue();
    }

    private org.mockito.stubbing.Answer<Integer> assignPaymentId(Long id) {
        return invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(id);
            return 1;
        };
    }

    private PayableEntity payable(Long id, String currencyCode, String rate, String originalAmount, String settledAmount) {
        PayableEntity payable = new PayableEntity();
        payable.setId(id);
        payable.setCompanyId(AUDIT.companyId());
        payable.setAccountBookId(AUDIT.accountBookId());
        payable.setDeletedFlag(0);
        payable.setDirection("INCREASE");
        payable.setSupplierId(SUPPLIER_ID);
        payable.setCurrencyCode(currencyCode);
        payable.setExchangeRate(new BigDecimal(rate));
        payable.setOriginalAmount(new BigDecimal(originalAmount));
        payable.setSettledAmount(new BigDecimal(settledAmount));
        payable.setBaseOriginalAmount(new BigDecimal(originalAmount).multiply(new BigDecimal(rate)));
        payable.setBaseSettledAmount(new BigDecimal(settledAmount).multiply(new BigDecimal(rate)));
        return payable;
    }

    private AuditMetadataFactory auditMetadataFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private PaymentNumberService paymentNumberService() {
        PaymentNumberService numberService = mock(PaymentNumberService.class);
        when(numberService.nextPaymentNo(any())).thenReturn("FP-TEST");
        return numberService;
    }
}
