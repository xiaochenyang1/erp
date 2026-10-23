package com.tuowei.erp.finance.receipt.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.finance.posting.SettlementPostingAmounts;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
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
 * 收款核销的已实现汇兑损益：结算汇率与应收入账汇率的差额落到核销明细、子账和过账口径上。
 */
class ReceiptExchangeDifferenceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            901L,
            11L,
            22L,
            LocalDateTime.of(2026, 9, 18, 10, 0)
    );
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 9, 18);
    private static final Long CUSTOMER_ID = 7001L;

    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptAllocationMapper receiptAllocationMapper = mock(ReceiptAllocationMapper.class);
    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final ReceiptQueryService queryService = mock(ReceiptQueryService.class);
    private final FinancePostingService postingService = mock(FinancePostingService.class);
    private final SettlementCurrencyService settlementCurrencyService = mock(SettlementCurrencyService.class);
    private final ReceiptCommandService service = new ReceiptCommandService(
            receiptMapper,
            receiptAllocationMapper,
            receivableMapper,
            receiptNumberService(),
            auditMetadataFactory(),
            mock(AccountPeriodGuard.class),
            queryService,
            postingService,
            settlementCurrencyService
    );

    @Test
    void settlingAboveTheBookingRateRecordsARealisedExchangeGain() {
        resolveSettlementCurrency("USD", "7.30");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(assignReceiptId(5001L));
        when(receivableMapper.selectById(6001L)).thenReturn(receivable(6001L, "USD", "7.20", "1000.00", "0.00"));
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenReturn(1);

        service.create(new ReceiptCreateRequest(
                CUSTOMER_ID,
                SETTLEMENT_DATE,
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("7.30"),
                null,
                List.of(new ReceiptAllocationRequest(6001L, new BigDecimal("1000.00")))
        ));

        ArgumentCaptor<ReceiptAllocationEntity> allocationCaptor = ArgumentCaptor.forClass(ReceiptAllocationEntity.class);
        verify(receiptAllocationMapper).insert(allocationCaptor.capture());
        ReceiptAllocationEntity allocation = allocationCaptor.getValue();
        assertThat(allocation.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(allocation.getBaseAmount()).isEqualByComparingTo("7300.000000");
        assertThat(allocation.getBaseSettledAmount()).isEqualByComparingTo("7200.000000");
        assertThat(allocation.getFxGainLossAmount()).isEqualByComparingTo("100.000000");

        // 全额核销后应收的本位币已核销额必须等于入账额，否则总账的 1122 永远轧不平。
        ReceivableEntity receivable = captureReceivable();
        assertThat(receivable.getSettledAmount()).isEqualByComparingTo("1000.00");
        assertThat(receivable.getBaseSettledAmount()).isEqualByComparingTo("7200.000000");
        assertThat(receivable.getStatus()).isEqualTo("SETTLED");

        SettlementPostingAmounts amounts = capturePosting();
        assertThat(amounts.reliefAmount()).isEqualByComparingTo("7200.00");
        assertThat(amounts.advanceAmount()).isEqualByComparingTo("0.00");
        assertThat(amounts.fxAmount()).isEqualByComparingTo("100.00");
        assertThat(amounts.cashAmount()).isEqualByComparingTo("7300.00");
    }

    @Test
    void settlingBelowTheBookingRateRecordsALossAndKeepsTheAdvanceAtTheSettlementRate() {
        resolveSettlementCurrency("USD", "7.10");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(assignReceiptId(5002L));
        when(receivableMapper.selectById(6002L)).thenReturn(receivable(6002L, "USD", "7.20", "1000.00", "0.00"));
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenReturn(1);

        // 收 1200 USD 只核销 1000 USD，多收的 200 USD 按结算汇率进预收，不产生汇兑损益。
        service.create(new ReceiptCreateRequest(
                CUSTOMER_ID,
                SETTLEMENT_DATE,
                new BigDecimal("1200.00"),
                "USD",
                new BigDecimal("7.10"),
                null,
                List.of(new ReceiptAllocationRequest(6002L, new BigDecimal("1000.00")))
        ));

        SettlementPostingAmounts amounts = capturePosting();
        assertThat(amounts.reliefAmount()).isEqualByComparingTo("7200.00");
        assertThat(amounts.advanceAmount()).isEqualByComparingTo("1420.00");
        assertThat(amounts.fxAmount()).isEqualByComparingTo("-100.00");
        assertThat(amounts.cashAmount()).isEqualByComparingTo("8520.00");
    }

    @Test
    void settlingAReceivableInAnotherCurrencyIsRejected() {
        resolveSettlementCurrency("USD", "7.30");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(assignReceiptId(5003L));
        when(receivableMapper.selectById(6003L)).thenReturn(receivable(6003L, "CNY", "1", "1000.00", "0.00"));

        assertThatThrownBy(() -> service.create(new ReceiptCreateRequest(
                CUSTOMER_ID,
                SETTLEMENT_DATE,
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("7.30"),
                null,
                List.of(new ReceiptAllocationRequest(6003L, new BigDecimal("1000.00")))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收款币种与应收币种不一致");
    }

    @Test
    void cancellingAPreV165ReceiptRebuildsTheReversalFromTheDocumentAndSubledgerRates() {
        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setId(5004L);
        receipt.setCompanyId(AUDIT.companyId());
        receipt.setAccountBookId(AUDIT.accountBookId());
        receipt.setReceiptNo("FR-5004");
        receipt.setReceiptDate(SETTLEMENT_DATE);
        receipt.setStatus("POSTED");
        receipt.setAmount(new BigDecimal("300.00"));
        receipt.setAllocatedAmount(new BigDecimal("300.00"));
        receipt.setCurrencyCode("CNY");
        receipt.setExchangeRate(BigDecimal.ONE);
        when(queryService.requireReceipt(5004L)).thenReturn(receipt);
        when(receiptMapper.updateById(any(ReceiptEntity.class))).thenReturn(1);

        // V165 之前落库的核销明细：base_amount / base_settled_amount 还是 0 默认值。
        ReceiptAllocationEntity legacyAllocation = new ReceiptAllocationEntity();
        legacyAllocation.setId(8001L);
        legacyAllocation.setReceivableId(6004L);
        legacyAllocation.setAmount(new BigDecimal("300.00"));
        legacyAllocation.setBaseAmount(BigDecimal.ZERO);
        legacyAllocation.setBaseSettledAmount(BigDecimal.ZERO);
        when(queryService.allocations(receipt)).thenReturn(List.of(legacyAllocation));
        when(receivableMapper.selectById(6004L)).thenReturn(receivable(6004L, "CNY", "1", "300.00", "300.00"));
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.cancel(5004L, new ReceiptCancelRequest("重复收款"));

        ArgumentCaptor<SettlementPostingAmounts> amountsCaptor = ArgumentCaptor.forClass(SettlementPostingAmounts.class);
        verify(postingService).recordReceiptCancellation(eq(receipt), amountsCaptor.capture(), eq(AUDIT));
        assertThat(amountsCaptor.getValue().reliefAmount()).isEqualByComparingTo("300.00");
        assertThat(amountsCaptor.getValue().fxAmount()).isEqualByComparingTo("0.00");
        assertThat(amountsCaptor.getValue().cashAmount()).isEqualByComparingTo("300.00");

        ReceivableEntity receivable = captureReceivable();
        assertThat(receivable.getSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(receivable.getBaseSettledAmount()).isEqualByComparingTo("0.000000");
    }

    private void resolveSettlementCurrency(String currencyCode, String rate) {
        when(settlementCurrencyService.resolve(any(), any(), any(), any()))
                .thenReturn(new SettlementCurrencyService.Resolution(currencyCode, new BigDecimal(rate)));
    }

    private ReceivableEntity captureReceivable() {
        ArgumentCaptor<ReceivableEntity> captor = ArgumentCaptor.forClass(ReceivableEntity.class);
        verify(receivableMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private SettlementPostingAmounts capturePosting() {
        ArgumentCaptor<SettlementPostingAmounts> captor = ArgumentCaptor.forClass(SettlementPostingAmounts.class);
        verify(postingService).recordReceipt(any(ReceiptEntity.class), captor.capture(), eq(AUDIT));
        return captor.getValue();
    }

    private org.mockito.stubbing.Answer<Integer> assignReceiptId(Long id) {
        return invocation -> {
            ReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(id);
            return 1;
        };
    }

    private ReceivableEntity receivable(Long id, String currencyCode, String rate, String originalAmount, String settledAmount) {
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setId(id);
        receivable.setCompanyId(AUDIT.companyId());
        receivable.setAccountBookId(AUDIT.accountBookId());
        receivable.setDeletedFlag(0);
        receivable.setDirection("INCREASE");
        receivable.setCustomerId(CUSTOMER_ID);
        receivable.setCurrencyCode(currencyCode);
        receivable.setExchangeRate(new BigDecimal(rate));
        receivable.setOriginalAmount(new BigDecimal(originalAmount));
        receivable.setSettledAmount(new BigDecimal(settledAmount));
        receivable.setBaseOriginalAmount(new BigDecimal(originalAmount).multiply(new BigDecimal(rate)));
        receivable.setBaseSettledAmount(new BigDecimal(settledAmount).multiply(new BigDecimal(rate)));
        return receivable;
    }

    private AuditMetadataFactory auditMetadataFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private ReceiptNumberService receiptNumberService() {
        ReceiptNumberService numberService = mock(ReceiptNumberService.class);
        when(numberService.nextReceiptNo(any())).thenReturn("FR-TEST");
        return numberService;
    }
}
