package com.tuowei.erp.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.service.PaymentNumberService;
import com.tuowei.erp.finance.payment.service.PaymentPostingService;
import com.tuowei.erp.finance.payment.service.PaymentQueryService;
import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.service.ReceiptNumberService;
import com.tuowei.erp.finance.receipt.service.ReceiptPostingService;
import com.tuowei.erp.finance.receipt.service.ReceiptQueryService;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceSettlementTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9901L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 18, 0)
    );
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 6, 8);
    private static final BigDecimal TEN = new BigDecimal("10.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final PaymentAllocationMapper paymentAllocationMapper = mock(PaymentAllocationMapper.class);
    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final PaymentNumberService paymentNumberService = mock(PaymentNumberService.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptAllocationMapper receiptAllocationMapper = mock(ReceiptAllocationMapper.class);
    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final ReceiptNumberService receiptNumberService = mock(ReceiptNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final AccountPeriodGuard accountPeriodGuard = mock(AccountPeriodGuard.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PaymentEntity.class);
        initTableInfo(PaymentAllocationEntity.class);
        initTableInfo(ReceiptEntity.class);
        initTableInfo(ReceiptAllocationEntity.class);
    }

    @Test
    void paymentListHandlesNullQueryAndScopesByCompanyAndAccountBook() {
        stubAudit();
        when(paymentMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PaymentEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        paymentService().list(null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PaymentEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paymentMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void paymentDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(paymentMapper.selectById(2001L)).thenReturn(activePayment(2001L, AUDIT.companyId(), 999L));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService().detail(2001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("付款单不存在");
    }

    @Test
    void paymentDetailScopesAllocationQueryByCompanyAndAccountBook() {
        stubAudit();
        when(paymentMapper.selectById(2004L)).thenReturn(activePayment(2004L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        paymentService().detail(2004L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PaymentAllocationEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paymentAllocationMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void paymentCreateRejectsPayableFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        AtomicReference<PaymentEntity> insertedPayment = new AtomicReference<>();
        when(paymentNumberService.nextPaymentNo(BIZ_DATE)).thenReturn("FP-2002");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(2002L);
            insertedPayment.set(payment);
            return 1;
        });
        when(payableMapper.selectById(3002L)).thenReturn(activePayable(3002L, AUDIT.companyId(), 999L));
        when(paymentAllocationMapper.insert(any(PaymentAllocationEntity.class))).thenReturn(1);
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);
        when(paymentMapper.selectById(2002L)).thenAnswer(invocation -> insertedPayment.get());
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        PaymentCreateRequest request = new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "跨账套应付",
                List.of(new PaymentAllocationRequest(3002L, TEN))
        );

        assertThatThrownBy(() -> paymentService().create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("应付记录不存在");
    }

    @Test
    void paymentCreateRejectsNullRequest() {
        assertThatThrownBy(() -> paymentService().create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("付款单请求不能为空");
    }

    @Test
    void paymentCreateRejectsNullAllocations() {
        assertThatThrownBy(() -> paymentService().create(new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "空付款核销",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("付款核销明细不能为空");
    }

    @Test
    void paymentCreateRejectsNullAllocationLine() {
        assertThatThrownBy(() -> paymentService().create(new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "空付款核销行",
                Collections.singletonList(null)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("付款核销明细不能为空");
    }

    @Test
    void paymentCreateWritesAllocationCompanyAndAccountBook() {
        stubAudit();
        AtomicReference<PaymentEntity> insertedPayment = new AtomicReference<>();
        AtomicReference<PaymentAllocationEntity> insertedAllocation = new AtomicReference<>();
        when(paymentNumberService.nextPaymentNo(BIZ_DATE)).thenReturn("FP-2005");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(2005L);
            insertedPayment.set(payment);
            return 1;
        });
        when(payableMapper.selectById(3005L)).thenReturn(activePayable(3005L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(paymentAllocationMapper.insert(any(PaymentAllocationEntity.class))).thenAnswer(invocation -> {
            insertedAllocation.set(invocation.getArgument(0));
            return 1;
        });
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);
        when(paymentMapper.selectById(2005L)).thenAnswer(invocation -> insertedPayment.get());
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        paymentService().create(new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "同账套应付",
                List.of(new PaymentAllocationRequest(3005L, TEN))
        ));

        assertThat(insertedAllocation.get())
                .hasFieldOrPropertyWithValue("companyId", AUDIT.companyId())
                .hasFieldOrPropertyWithValue("accountBookId", AUDIT.accountBookId());
    }

    @Test
    void paymentCreateFailsWhenPaymentInsertDoesNotPersistRecord() {
        stubAudit();
        AtomicReference<PaymentEntity> insertedPayment = new AtomicReference<>();
        when(paymentNumberService.nextPaymentNo(BIZ_DATE)).thenReturn("FP-2006");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(2006L);
            insertedPayment.set(payment);
            return 0;
        });
        when(payableMapper.selectById(3006L)).thenReturn(activePayable(3006L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(paymentAllocationMapper.insert(any(PaymentAllocationEntity.class))).thenReturn(1);
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);
        when(paymentMapper.selectById(2006L)).thenAnswer(invocation -> insertedPayment.get());
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService().create(new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "付款主表写入失败",
                List.of(new PaymentAllocationRequest(3006L, TEN))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存付款单失败");

        verify(paymentAllocationMapper, never()).insert(any(PaymentAllocationEntity.class));
    }

    @Test
    void paymentCreateFailsWhenAllocationInsertDoesNotPersistRecord() {
        stubAudit();
        AtomicReference<PaymentEntity> insertedPayment = new AtomicReference<>();
        when(paymentNumberService.nextPaymentNo(BIZ_DATE)).thenReturn("FP-2007");
        when(paymentMapper.insert(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(2007L);
            insertedPayment.set(payment);
            return 1;
        });
        when(payableMapper.selectById(3007L)).thenReturn(activePayable(3007L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(paymentAllocationMapper.insert(any(PaymentAllocationEntity.class))).thenReturn(0);
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);
        when(paymentMapper.selectById(2007L)).thenAnswer(invocation -> insertedPayment.get());
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService().create(new PaymentCreateRequest(
                7001L,
                BIZ_DATE,
                TEN,
                "付款明细写入失败",
                List.of(new PaymentAllocationRequest(3007L, TEN))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存付款核销明细失败");

        verify(payableMapper, never()).updateById(any(PayableEntity.class));
    }

    @Test
    void paymentCancelRejectsPayableFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(paymentMapper.selectById(2003L)).thenReturn(activePayment(2003L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(paymentMapper.updateById(any(PaymentEntity.class))).thenReturn(1);
        when(paymentAllocationMapper.selectList(any())).thenReturn(List.of(paymentAllocation(4003L, 2003L, 3003L)));
        when(payableMapper.selectById(3003L)).thenReturn(activePayable(3003L, AUDIT.companyId(), 999L));
        when(payableMapper.updateById(any(PayableEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> paymentService().cancel(2003L, new PaymentCancelRequest("跨账套核销")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("付款核销的应付记录不存在，不能作废付款单");
    }

    @Test
    void receiptListHandlesNullQueryAndScopesByCompanyAndAccountBook() {
        stubAudit();
        when(receiptMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ReceiptEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        receiptService().list(null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceiptEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receiptMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void receiptDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(receiptMapper.selectById(5001L)).thenReturn(activeReceipt(5001L, AUDIT.companyId(), 999L));
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> receiptService().detail(5001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收款单不存在");
    }

    @Test
    void receiptDetailScopesAllocationQueryByCompanyAndAccountBook() {
        stubAudit();
        when(receiptMapper.selectById(5004L)).thenReturn(activeReceipt(5004L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        receiptService().detail(5004L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceiptAllocationEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receiptAllocationMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void receiptCreateRejectsReceivableFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        AtomicReference<ReceiptEntity> insertedReceipt = new AtomicReference<>();
        when(receiptNumberService.nextReceiptNo(BIZ_DATE)).thenReturn("FR-5002");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(invocation -> {
            ReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(5002L);
            insertedReceipt.set(receipt);
            return 1;
        });
        when(receivableMapper.selectById(6002L)).thenReturn(activeReceivable(6002L, AUDIT.companyId(), 999L));
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenReturn(1);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptMapper.selectById(5002L)).thenAnswer(invocation -> insertedReceipt.get());
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        ReceiptCreateRequest request = new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "跨账套应收",
                List.of(new ReceiptAllocationRequest(6002L, TEN))
        );

        assertThatThrownBy(() -> receiptService().create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("应收记录不存在");
    }

    @Test
    void receiptCreateRejectsNullRequest() {
        assertThatThrownBy(() -> receiptService().create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收款单请求不能为空");
    }

    @Test
    void receiptCreateRejectsNullAllocations() {
        assertThatThrownBy(() -> receiptService().create(new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "空收款核销",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收款核销明细不能为空");
    }

    @Test
    void receiptCreateRejectsNullAllocationLine() {
        assertThatThrownBy(() -> receiptService().create(new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "空收款核销行",
                Collections.singletonList(null)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收款核销明细不能为空");
    }

    @Test
    void receiptCreateWritesAllocationCompanyAndAccountBook() {
        stubAudit();
        AtomicReference<ReceiptEntity> insertedReceipt = new AtomicReference<>();
        AtomicReference<ReceiptAllocationEntity> insertedAllocation = new AtomicReference<>();
        when(receiptNumberService.nextReceiptNo(BIZ_DATE)).thenReturn("FR-5005");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(invocation -> {
            ReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(5005L);
            insertedReceipt.set(receipt);
            return 1;
        });
        when(receivableMapper.selectById(6005L)).thenReturn(activeReceivable(6005L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenAnswer(invocation -> {
            insertedAllocation.set(invocation.getArgument(0));
            return 1;
        });
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptMapper.selectById(5005L)).thenAnswer(invocation -> insertedReceipt.get());
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        receiptService().create(new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "同账套应收",
                List.of(new ReceiptAllocationRequest(6005L, TEN))
        ));

        assertThat(insertedAllocation.get())
                .hasFieldOrPropertyWithValue("companyId", AUDIT.companyId())
                .hasFieldOrPropertyWithValue("accountBookId", AUDIT.accountBookId());
    }

    @Test
    void receiptCreateFailsWhenReceiptInsertDoesNotPersistRecord() {
        stubAudit();
        AtomicReference<ReceiptEntity> insertedReceipt = new AtomicReference<>();
        when(receiptNumberService.nextReceiptNo(BIZ_DATE)).thenReturn("FR-5006");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(invocation -> {
            ReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(5006L);
            insertedReceipt.set(receipt);
            return 0;
        });
        when(receivableMapper.selectById(6006L)).thenReturn(activeReceivable(6006L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenReturn(1);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptMapper.selectById(5006L)).thenAnswer(invocation -> insertedReceipt.get());
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> receiptService().create(new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "收款主表写入失败",
                List.of(new ReceiptAllocationRequest(6006L, TEN))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存收款单失败");

        verify(receiptAllocationMapper, never()).insert(any(ReceiptAllocationEntity.class));
    }

    @Test
    void receiptCreateFailsWhenAllocationInsertDoesNotPersistRecord() {
        stubAudit();
        AtomicReference<ReceiptEntity> insertedReceipt = new AtomicReference<>();
        when(receiptNumberService.nextReceiptNo(BIZ_DATE)).thenReturn("FR-5007");
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenAnswer(invocation -> {
            ReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(5007L);
            insertedReceipt.set(receipt);
            return 1;
        });
        when(receivableMapper.selectById(6007L)).thenReturn(activeReceivable(6007L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(receiptAllocationMapper.insert(any(ReceiptAllocationEntity.class))).thenReturn(0);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);
        when(receiptMapper.selectById(5007L)).thenAnswer(invocation -> insertedReceipt.get());
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> receiptService().create(new ReceiptCreateRequest(
                8001L,
                BIZ_DATE,
                TEN,
                "收款明细写入失败",
                List.of(new ReceiptAllocationRequest(6007L, TEN))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存收款核销明细失败");

        verify(receivableMapper, never()).updateById(any(ReceivableEntity.class));
    }

    @Test
    void receiptCancelRejectsReceivableFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(receiptMapper.selectById(5003L)).thenReturn(activeReceipt(5003L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(receiptMapper.updateById(any(ReceiptEntity.class))).thenReturn(1);
        when(receiptAllocationMapper.selectList(any())).thenReturn(List.of(receiptAllocation(7003L, 5003L, 6003L)));
        when(receivableMapper.selectById(6003L)).thenReturn(activeReceivable(6003L, AUDIT.companyId(), 999L));
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> receiptService().cancel(5003L, new ReceiptCancelRequest("跨账套核销")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("收款核销的应收记录不存在，不能作废收款单");
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void assertTenantScoped(AbstractWrapper<?, ?, ?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private PaymentService paymentService() {
        PaymentQueryService queryService = new PaymentQueryService(
                paymentMapper,
                paymentAllocationMapper,
                auditMetadataFactory
        );
        PaymentPostingService postingService = new PaymentPostingService(
                paymentMapper,
                paymentAllocationMapper,
                payableMapper,
                auditMetadataFactory,
                accountPeriodGuard,
                queryService
        );
        return new PaymentService(
                paymentMapper,
                paymentNumberService,
                auditMetadataFactory,
                accountPeriodGuard,
                queryService,
                postingService
        );
    }

    private ReceiptService receiptService() {
        ReceiptQueryService queryService = new ReceiptQueryService(
                receiptMapper,
                receiptAllocationMapper,
                auditMetadataFactory
        );
        ReceiptPostingService postingService = new ReceiptPostingService(
                receiptMapper,
                receiptAllocationMapper,
                receivableMapper,
                auditMetadataFactory,
                accountPeriodGuard,
                queryService
        );
        return new ReceiptService(
                receiptMapper,
                receiptNumberService,
                auditMetadataFactory,
                accountPeriodGuard,
                queryService,
                postingService
        );
    }

    private PaymentEntity activePayment(Long id, Long companyId, Long accountBookId) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(id);
        payment.setCompanyId(companyId);
        payment.setAccountBookId(accountBookId);
        payment.setPaymentNo("FP-" + id);
        payment.setSupplierId(7001L);
        payment.setPaymentDate(BIZ_DATE);
        payment.setAmount(TEN);
        payment.setAllocatedAmount(TEN);
        payment.setStatus("POSTED");
        payment.setDeletedFlag(0);
        payment.setVersion(0);
        return payment;
    }

    private PayableEntity activePayable(Long id, Long companyId, Long accountBookId) {
        PayableEntity payable = new PayableEntity();
        payable.setId(id);
        payable.setCompanyId(companyId);
        payable.setAccountBookId(accountBookId);
        payable.setDirection("INCREASE");
        payable.setSupplierId(7001L);
        payable.setOriginalAmount(HUNDRED);
        payable.setSettledAmount(TEN);
        payable.setStatus("PARTIALLY_SETTLED");
        payable.setDeletedFlag(0);
        payable.setVersion(0);
        return payable;
    }

    private PaymentAllocationEntity paymentAllocation(Long id, Long paymentId, Long payableId) {
        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setId(id);
        allocation.setPaymentId(paymentId);
        allocation.setPayableId(payableId);
        allocation.setAmount(TEN);
        allocation.setVersion(0);
        return allocation;
    }

    private ReceiptEntity activeReceipt(Long id, Long companyId, Long accountBookId) {
        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setId(id);
        receipt.setCompanyId(companyId);
        receipt.setAccountBookId(accountBookId);
        receipt.setReceiptNo("FR-" + id);
        receipt.setCustomerId(8001L);
        receipt.setReceiptDate(BIZ_DATE);
        receipt.setAmount(TEN);
        receipt.setAllocatedAmount(TEN);
        receipt.setStatus("POSTED");
        receipt.setDeletedFlag(0);
        receipt.setVersion(0);
        return receipt;
    }

    private ReceivableEntity activeReceivable(Long id, Long companyId, Long accountBookId) {
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setId(id);
        receivable.setCompanyId(companyId);
        receivable.setAccountBookId(accountBookId);
        receivable.setDirection("INCREASE");
        receivable.setCustomerId(8001L);
        receivable.setOriginalAmount(HUNDRED);
        receivable.setSettledAmount(TEN);
        receivable.setStatus("PARTIALLY_SETTLED");
        receivable.setDeletedFlag(0);
        receivable.setVersion(0);
        return receivable;
    }

    private ReceiptAllocationEntity receiptAllocation(Long id, Long receiptId, Long receivableId) {
        ReceiptAllocationEntity allocation = new ReceiptAllocationEntity();
        allocation.setId(id);
        allocation.setReceiptId(receiptId);
        allocation.setReceivableId(receivableId);
        allocation.setAmount(TEN);
        allocation.setVersion(0);
        return allocation;
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
