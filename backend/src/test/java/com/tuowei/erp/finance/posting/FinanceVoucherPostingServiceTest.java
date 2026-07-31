package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
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
