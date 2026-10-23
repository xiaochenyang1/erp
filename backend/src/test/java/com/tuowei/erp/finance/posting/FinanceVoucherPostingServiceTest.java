package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class FinanceVoucherPostingServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            502L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 31, 16, 30)
    );

    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final FinanceVoucherPostingService service = new FinanceVoucherPostingService(
            voucherMapper,
            voucherEntryMapper,
            accountSubjectMapper
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
        initTableInfo(AccountSubjectEntity.class);
    }

    @Test
    void purchaseReceiptWithTaxCreatesTenantScopedVoucherAndThreeBalancedEntries() {
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(801L);
            return 1;
        });
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8101L, "1001"))
                .thenReturn(activeSubject(8102L, "222101"))
                .thenReturn(activeSubject(8103L, "2202"));
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setId(701L);
        receipt.setReceiptNo("PR-701");
        receipt.setReceiptDate(LocalDate.of(2026, 7, 31));

        service.recordPurchaseReceipt(
                receipt,
                new BigDecimal("100.00"),
                new BigDecimal("13.00"),
                new BigDecimal("113.00"),
                AUDIT
        );

        ArgumentCaptor<LambdaQueryWrapper<VoucherEntity>> voucherWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherMapper).selectOne(voucherWrapperCaptor.capture());
        assertTenantScoped(voucherWrapperCaptor.getValue());

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity voucher = voucherCaptor.getValue();
        assertThat(voucher.getVoucherNo()).isEqualTo("VO-PURCHASE_RECEIPT-701");
        assertThat(voucher.getAmount()).isEqualByComparingTo("113.00");
        assertThat(voucher.getStatus()).isEqualTo("POSTED");
        assertThat(voucher.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(voucher.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(voucher.getCreatedTime()).isEqualTo(AUDIT.now());

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(3)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
                .extracting(
                        VoucherEntryEntity::getLineNo,
                        VoucherEntryEntity::getSubjectCode,
                        VoucherEntryEntity::getDebitAmount,
                        VoucherEntryEntity::getCreditAmount
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "1001", new BigDecimal("100.00"), new BigDecimal("0.00")),
                        org.assertj.core.groups.Tuple.tuple(2, "222101", new BigDecimal("13.00"), new BigDecimal("0.00")),
                        org.assertj.core.groups.Tuple.tuple(3, "2202", new BigDecimal("0.00"), new BigDecimal("113.00"))
                );

        assertAllEntryAndSubjectQueriesAreTenantScoped();
    }

    @Test
    void salesDeliveryReusesExistingVoucherAndAppendsOnlyMissingCostEntries() {
        VoucherEntity voucher = existingVoucher(802L, LocalDate.of(2026, 7, 30));
        when(voucherMapper.selectOne(any())).thenReturn(voucher);
        when(voucherEntryMapper.selectCount(any())).thenReturn(1L, 1L, 0L);
        VoucherEntryEntity existingEntry = new VoucherEntryEntity();
        existingEntry.setLineNo(2);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of(existingEntry));
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8201L, "6402"))
                .thenReturn(activeSubject(8202L, "1001"));
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(702L);
        delivery.setDeliveryNo("SD-702");
        delivery.setDeliveryDate(LocalDate.of(2026, 7, 30));

        service.recordSalesDelivery(
                delivery,
                new BigDecimal("113.00"),
                new BigDecimal("45.678"),
                AUDIT
        );

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues())
                .extracting(
                        VoucherEntryEntity::getLineNo,
                        VoucherEntryEntity::getSubjectCode,
                        VoucherEntryEntity::getDebitAmount,
                        VoucherEntryEntity::getCreditAmount
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3, "6402", new BigDecimal("45.68"), new BigDecimal("0.00")),
                        org.assertj.core.groups.Tuple.tuple(4, "1001", new BigDecimal("0.00"), new BigDecimal("45.68"))
                );
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> entryListWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(entryListWrapperCaptor.capture());
        assertTenantScoped(entryListWrapperCaptor.getValue());
        assertAllEntryAndSubjectQueriesAreTenantScoped();
    }

    @Test
    void salesCostPairIsIdempotentWhenBothSidesAlreadyExist() {
        when(voucherMapper.selectOne(any())).thenReturn(existingVoucher(803L, LocalDate.of(2026, 7, 29)));
        when(voucherEntryMapper.selectCount(any())).thenReturn(1L, 1L, 1L);
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(703L);
        delivery.setDeliveryNo("SD-703");
        delivery.setDeliveryDate(LocalDate.of(2026, 7, 29));

        service.recordSalesDelivery(delivery, new BigDecimal("50.00"), new BigDecimal("20.00"), AUDIT);

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verifyNoInteractions(accountSubjectMapper);
    }

    @Test
    void emptyInventoryAdjustmentDoesNotCreateVoucherOrResolveSubjects() {
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();

        service.recordInventoryAdjustment(adjustment, null, AUDIT);
        service.recordInventoryAdjustment(adjustment, List.of(), AUDIT);

        verifyNoInteractions(voucherMapper, voucherEntryMapper, accountSubjectMapper);
    }

    @Test
    void fullyAllocatedReceiptDebitsCashAndCreditsReceivable() {
        stubFreshVoucher(811L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8301L, "1002"))
                .thenReturn(activeSubject(8302L, "1122"));

        service.recordReceipt(receipt(711L, "FR-711"), noFxAmounts("100.00", "0.00"), AUDIT);

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getVoucherNo()).isEqualTo("VO-RECEIPT-711");
        assertThat(voucherCaptor.getValue().getAmount()).isEqualByComparingTo("100.00");

        assertEntries(2).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("100.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1122", new BigDecimal("0.00"), new BigDecimal("100.00"))
        );
        assertAllEntryAndSubjectQueriesAreTenantScoped();
    }

    @Test
    void receiptBeyondAllocationBooksRemainderAsCustomerAdvance() {
        stubFreshVoucher(812L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8303L, "1002"))
                .thenReturn(activeSubject(8304L, "1122"))
                .thenReturn(activeSubject(8305L, "2203"));

        service.recordReceipt(receipt(712L, "FR-712"), noFxAmounts("60.00", "40.00"), AUDIT);

        assertEntries(3).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("100.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1122", new BigDecimal("0.00"), new BigDecimal("60.00")),
                org.assertj.core.groups.Tuple.tuple(3, "2203", new BigDecimal("0.00"), new BigDecimal("40.00"))
        );
    }

    @Test
    void paymentDebitsPayableAndAdvanceThenCreditsCashLast() {
        stubFreshVoucher(813L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8306L, "1002"))
                .thenReturn(activeSubject(8307L, "2202"))
                .thenReturn(activeSubject(8308L, "1123"));

        service.recordPayment(payment(713L, "FP-713"), noFxAmounts("80.00", "20.00"), AUDIT);

        assertEntries(3).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "2202", new BigDecimal("80.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1123", new BigDecimal("20.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(3, "1002", new BigDecimal("0.00"), new BigDecimal("100.00"))
        );
    }

    @Test
    void cancellationVouchersMirrorTheOriginalLegs() {
        stubFreshVoucher(814L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8309L, "1002"))
                .thenReturn(activeSubject(8310L, "1122"));

        service.recordReceiptCancellation(receipt(714L, "FR-714"), noFxAmounts("30.00", "0.00"), AUDIT);

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getVoucherNo()).isEqualTo("VO-RECEIPT_REVERSAL-714");

        assertEntries(2).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1122", new BigDecimal("30.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1002", new BigDecimal("0.00"), new BigDecimal("30.00"))
        );
    }

    @Test
    void receiptAtAHigherSettlementRateCreditsRealisedExchangeGain() {
        stubFreshVoucher(817L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8313L, "1002"))
                .thenReturn(activeSubject(8314L, "1122"))
                .thenReturn(activeSubject(8315L, "6061"));

        // 1000 USD 应收按 7.20 入账，7.30 收款：资金 7300 = 应收冲减 7200 + 汇兑收益 100。
        service.recordReceipt(
                receipt(717L, "FR-717"),
                SettlementPostingAmounts.fromAllocations(new BigDecimal("7300.00"), new BigDecimal("7200.00"), BigDecimal.ZERO),
                AUDIT
        );

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getAmount()).isEqualByComparingTo("7300.00");

        assertEntries(3).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("7300.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1122", new BigDecimal("0.00"), new BigDecimal("7200.00")),
                org.assertj.core.groups.Tuple.tuple(3, "6061", new BigDecimal("0.00"), new BigDecimal("100.00"))
        );
    }

    @Test
    void receiptAtALowerSettlementRateDebitsRealisedExchangeLossAndStaysBalanced() {
        stubFreshVoucher(818L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8316L, "1002"))
                .thenReturn(activeSubject(8317L, "1122"))
                .thenReturn(activeSubject(8318L, "6061"));

        // 1000 USD 应收按 7.30 入账，7.20 收款：资金 7200 + 汇兑损失 100 = 应收冲减 7300。
        service.recordReceipt(
                receipt(718L, "FR-718"),
                SettlementPostingAmounts.fromAllocations(new BigDecimal("7200.00"), new BigDecimal("7300.00"), BigDecimal.ZERO),
                AUDIT
        );

        assertEntries(3).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("7200.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1122", new BigDecimal("0.00"), new BigDecimal("7300.00")),
                org.assertj.core.groups.Tuple.tuple(3, "6061", new BigDecimal("100.00"), new BigDecimal("0.00"))
        );
    }

    @Test
    void paymentAtAHigherSettlementRateDebitsRealisedExchangeLossBeforeCash() {
        stubFreshVoucher(819L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8319L, "1002"))
                .thenReturn(activeSubject(8320L, "2202"))
                .thenReturn(activeSubject(8321L, "1123"))
                .thenReturn(activeSubject(8322L, "6061"));

        // 1000 USD 应付按 7.20 入账，7.30 付款并多付 100 USD 形成预付：
        // 应付冲减 7200 + 预付 730 + 汇兑损失 100 = 资金 8030。
        service.recordPayment(
                payment(719L, "FP-719"),
                SettlementPostingAmounts.fromAllocations(new BigDecimal("7300.00"), new BigDecimal("7200.00"), new BigDecimal("730.00")),
                AUDIT
        );

        assertEntries(4).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "2202", new BigDecimal("7200.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1123", new BigDecimal("730.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(3, "6061", new BigDecimal("100.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(4, "1002", new BigDecimal("0.00"), new BigDecimal("8030.00"))
        );
    }

    @Test
    void paymentCancellationMirrorsEveryLegIncludingExchangeDifference() {
        stubFreshVoucher(820L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8323L, "1002"))
                .thenReturn(activeSubject(8324L, "2202"))
                .thenReturn(activeSubject(8325L, "6061"));

        service.recordPaymentCancellation(
                payment(720L, "FP-720"),
                SettlementPostingAmounts.fromAllocations(new BigDecimal("7300.00"), new BigDecimal("7200.00"), BigDecimal.ZERO),
                AUDIT
        );

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getVoucherNo()).isEqualTo("VO-PAYMENT_REVERSAL-720");

        assertEntries(3).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("7300.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "2202", new BigDecimal("0.00"), new BigDecimal("7200.00")),
                org.assertj.core.groups.Tuple.tuple(3, "6061", new BigDecimal("0.00"), new BigDecimal("100.00"))
        );
    }

    @Test
    void baseCurrencySettlementKeepsTheTwoLeggedVoucherWithoutAnExchangeLine() {
        stubFreshVoucher(821L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8326L, "1002"))
                .thenReturn(activeSubject(8327L, "1122"));

        service.recordReceipt(
                receipt(721L, "FR-721"),
                SettlementPostingAmounts.fromAllocations(new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO),
                AUDIT
        );

        assertEntries(2).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "1002", new BigDecimal("500.00"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(2, "1122", new BigDecimal("0.00"), new BigDecimal("500.00"))
        );
    }

    @Test
    void settlementVoucherIsIdempotentAndRejectsZeroAmount() {
        when(voucherMapper.selectOne(any())).thenReturn(existingVoucher(815L, LocalDate.of(2026, 7, 28)));
        when(voucherEntryMapper.selectCount(any())).thenReturn(1L);

        service.recordPayment(payment(715L, "FP-715"), noFxAmounts("10.00", "0.00"), AUDIT);

        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verifyNoInteractions(accountSubjectMapper);

        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.recordPayment(payment(716L, "FP-716"), noFxAmounts("0.00", "0.00"), AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收付款凭证金额必须大于0");
    }

    @Test
    void reusesVoucherWonByConcurrentSourceInsert() {
        VoucherEntity winner = existingVoucher(816L, LocalDate.of(2026, 7, 27));
        when(voucherMapper.selectOne(any())).thenReturn(null, winner);
        when(voucherMapper.insert(any(VoucherEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate source"));
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8311L, "1122"))
                .thenReturn(activeSubject(8312L, "6001"));

        service.recordTwoSidedVoucher(
                "SALES_DELIVERY",
                716L,
                "SD-716",
                LocalDate.of(2026, 7, 27),
                new BigDecimal("25.00"),
                "销售出库凭证",
                "1122",
                "6001",
                "销售出库凭证",
                AUDIT
        );

        verify(voucherMapper).insert(any(VoucherEntity.class));
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntity>> lookups = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherMapper, times(2)).selectOne(lookups.capture());
        assertThat(lookups.getAllValues().get(1).getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("for update");
        ArgumentCaptor<VoucherEntryEntity> entries = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(2)).insert(entries.capture());
        assertThat(entries.getAllValues()).allSatisfy(entry -> assertThat(entry.getVoucherId()).isEqualTo(816L));
    }

    @Test
    void reusesVoucherLineWonByConcurrentEntryInsert() {
        VoucherEntity voucher = existingVoucher(817L, LocalDate.of(2026, 7, 26));
        VoucherEntryEntity winner = new VoucherEntryEntity();
        winner.setVoucherId(817L);
        winner.setLineNo(1);
        winner.setSubjectCode("1122");
        winner.setDebitAmount(new BigDecimal("25.00"));
        winner.setCreditAmount(new BigDecimal("0.00"));
        when(voucherMapper.selectOne(any())).thenReturn(voucher);
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(activeSubject(8313L, "1122"))
                .thenReturn(activeSubject(8314L, "6001"));
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate voucher line"))
                .thenReturn(1);
        when(voucherEntryMapper.selectOne(any())).thenReturn(winner);

        service.recordTwoSidedVoucher(
                "SALES_DELIVERY",
                717L,
                "SD-717",
                LocalDate.of(2026, 7, 26),
                new BigDecimal("25.00"),
                "销售出库凭证",
                "1122",
                "6001",
                "销售出库凭证",
                AUDIT
        );

        verify(voucherEntryMapper, times(2)).insert(any(VoucherEntryEntity.class));
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> lookup = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectOne(lookup.capture());
        assertThat(lookup.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("voucher_id", "line_no", "for update");
    }

    @Test
    void reusesDefaultSubjectWonByConcurrentInsert() {
        VoucherEntity voucher = existingVoucher(818L, LocalDate.of(2026, 7, 25));
        when(voucherMapper.selectOne(any())).thenReturn(voucher);
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(accountSubjectMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(activeSubject(8315L, "1122"))
                .thenReturn(activeSubject(8316L, "6001"));
        when(accountSubjectMapper.insert(any(AccountSubjectEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate subject"));

        service.recordTwoSidedVoucher(
                "SALES_DELIVERY",
                718L,
                "SD-718",
                LocalDate.of(2026, 7, 25),
                new BigDecimal("25.00"),
                "销售出库凭证",
                "1122",
                "6001",
                "销售出库凭证",
                AUDIT
        );

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> lookups =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper, times(3)).selectOne(lookups.capture());
        assertThat(lookups.getAllValues().get(1).getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("for update");
        verify(voucherEntryMapper, times(2)).insert(any(VoucherEntryEntity.class));
    }

    private void stubFreshVoucher(Long voucherId) {
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(voucherId);
            return 1;
        });
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
    }

    private org.assertj.core.api.AbstractListAssert<?, ?, org.assertj.core.groups.Tuple,
            ? extends org.assertj.core.api.AbstractAssert<?, org.assertj.core.groups.Tuple>> assertEntries(int expectedCount) {
        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(expectedCount)).insert(entryCaptor.capture());
        return assertThat(entryCaptor.getAllValues()).extracting(
                VoucherEntryEntity::getLineNo,
                VoucherEntryEntity::getSubjectCode,
                VoucherEntryEntity::getDebitAmount,
                VoucherEntryEntity::getCreditAmount
        );
    }

    /** 本位币结算：核销冲减额与预收预付额直接成腿，没有汇兑差额。 */
    private SettlementPostingAmounts noFxAmounts(String reliefAmount, String advanceAmount) {
        return SettlementPostingAmounts.withoutExchangeDifference(
                new BigDecimal(reliefAmount),
                new BigDecimal(advanceAmount)
        );
    }

    private ReceiptEntity receipt(Long id, String receiptNo) {
        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setId(id);
        receipt.setReceiptNo(receiptNo);
        receipt.setReceiptDate(LocalDate.of(2026, 7, 31));
        return receipt;
    }

    private PaymentEntity payment(Long id, String paymentNo) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setPaymentDate(LocalDate.of(2026, 7, 31));
        return payment;
    }

    private void assertAllEntryAndSubjectQueriesAreTenantScoped() {
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> entryWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper, atLeastOnce()).selectCount(entryWrapperCaptor.capture());
        assertThat(entryWrapperCaptor.getAllValues()).allSatisfy(this::assertTenantScoped);

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> subjectWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper, atLeastOnce()).selectOne(subjectWrapperCaptor.capture());
        assertThat(subjectWrapperCaptor.getAllValues()).allSatisfy(this::assertTenantScoped);
    }

    private VoucherEntity existingVoucher(Long id, LocalDate bizDate) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(AUDIT.companyId());
        voucher.setAccountBookId(AUDIT.accountBookId());
        voucher.setBizDate(bizDate);
        return voucher;
    }

    private AccountSubjectEntity activeSubject(Long id, String code) {
        AccountSubjectEntity subject = new AccountSubjectEntity();
        subject.setId(id);
        subject.setCompanyId(AUDIT.companyId());
        subject.setAccountBookId(AUDIT.accountBookId());
        subject.setSubjectCode(code);
        subject.setSubjectName("subject-" + code);
        subject.setStatus("ACTIVE");
        subject.setDeletedFlag(0);
        return subject;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id");
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
