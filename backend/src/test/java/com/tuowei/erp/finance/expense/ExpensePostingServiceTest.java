package com.tuowei.erp.finance.expense;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.service.ExpensePostingService;
import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ExpensePostingServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9701L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 13, 9, 30)
    );
    private static final Long EXPENSE_ID = 9101L;
    private static final Long VOUCHER_ID = 9201L;
    private static final Long REVERSAL_VOUCHER_ID = 9202L;

    private final ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AccountSubjectService accountSubjectService = mock(AccountSubjectService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final ExpenseQueryService expenseQueryService = mock(ExpenseQueryService.class);
    private final AccountPeriodGuard accountPeriodGuard = mock(AccountPeriodGuard.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void postCreatesBalancedVoucherEntriesBeforeUpdatingExpenseHead() {
        ExpenseEntity expense = expense("APPROVED");
        AccountSubjectEntity expenseSubject = subject(9301L, "6601", "差旅费");
        AccountSubjectEntity paymentSubject = subject(9302L, "1002", "银行存款");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectService.requireActiveSubject(9301L, "费用科目不存在或已停用"))
                .thenReturn(expenseSubject);
        when(accountSubjectService.requireActiveSubject(9302L, "支付科目不存在或已停用"))
                .thenReturn(paymentSubject);
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(VOUCHER_ID);
            return 1;
        });
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);
        when(expenseMapper.updateById(expense)).thenReturn(1);
        ExpenseResponse expected = response();
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        ExpenseResponse actual = service().post(EXPENSE_ID);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        VoucherEntity voucher = voucherCaptor.getValue();
        assertThat(voucher.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(voucher.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(voucher.getVoucherNo()).isEqualTo("VO-EXPENSE-" + EXPENSE_ID);
        assertThat(voucher.getSourceType()).isEqualTo("EXPENSE");
        assertThat(voucher.getSourceId()).isEqualTo(EXPENSE_ID);
        assertThat(voucher.getAmount()).isEqualByComparingTo("12.34");
        assertThat(voucher.getStatus()).isEqualTo("POSTED");

        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues()).satisfiesExactly(
                debit -> {
                    assertThat(debit.getVoucherId()).isEqualTo(VOUCHER_ID);
                    assertThat(debit.getLineNo()).isEqualTo(1);
                    assertThat(debit.getSubjectCode()).isEqualTo("6601");
                    assertThat(debit.getDebitAmount()).isEqualByComparingTo("12.34");
                    assertThat(debit.getCreditAmount()).isEqualByComparingTo("0.00");
                },
                credit -> {
                    assertThat(credit.getVoucherId()).isEqualTo(VOUCHER_ID);
                    assertThat(credit.getLineNo()).isEqualTo(2);
                    assertThat(credit.getSubjectCode()).isEqualTo("1002");
                    assertThat(credit.getDebitAmount()).isEqualByComparingTo("0.00");
                    assertThat(credit.getCreditAmount()).isEqualByComparingTo("12.34");
                }
        );
        assertThat(expense.getStatus()).isEqualTo("POSTED");
        assertThat(expense.getVoucherId()).isEqualTo(VOUCHER_ID);
        InOrder order = inOrder(voucherMapper, voucherEntryMapper, expenseMapper, expenseQueryService);
        order.verify(voucherMapper).insert(any(VoucherEntity.class));
        order.verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(any(VoucherEntryEntity.class));
        order.verify(expenseMapper).updateById(expense);
        order.verify(expenseQueryService).detail(EXPENSE_ID);
    }

    @Test
    void postReusesExistingVoucherAndEntriesWithoutDuplicateWrites() {
        ExpenseEntity expense = expense("APPROVED");
        VoucherEntity existing = voucher(VOUCHER_ID, "EXPENSE");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectService.requireActiveSubject(any(Long.class), anyString()))
                .thenReturn(subject(9301L, "6601", "费用"));
        when(voucherMapper.selectOne(any())).thenReturn(existing);
        when(voucherEntryMapper.selectCount(any())).thenReturn(2L);
        when(expenseMapper.updateById(expense)).thenReturn(1);
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(response());

        service().post(EXPENSE_ID);

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(expenseMapper).updateById(expense);
    }

    @Test
    void postReturnsImmediatelyWhenExpenseAlreadyPosted() {
        ExpenseEntity expense = expense("POSTED");
        ExpenseResponse expected = response();
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(expenseQueryService.toResponse(expense)).thenReturn(expected);

        assertThat(service().post(EXPENSE_ID)).isSameAs(expected);

        verify(attachmentService, never()).requireIfConfigured(any(), any());
        verify(accountPeriodGuard, never()).requireOpen(any(), any());
        verify(voucherMapper, never()).selectOne(any());
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void reverseCreatesSwappedEntriesWithTenantScopedIdempotencyCheck() {
        ExpenseEntity expense = expense("POSTED");
        expense.setVoucherId(VOUCHER_ID);
        VoucherEntity originalVoucher = voucher(VOUCHER_ID, "EXPENSE");
        VoucherEntryEntity originalDebit = entry(1, "12.34", "0", "差旅费");
        VoucherEntryEntity originalCredit = entry(2, "0", "12.34", "银行存款");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseQueryService.findExpenseVoucher(expense)).thenReturn(originalVoucher);
        when(expenseQueryService.voucherEntries(VOUCHER_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(List.of(originalDebit, originalCredit));
        when(expenseQueryService.findReversalVoucher(expense)).thenReturn(null);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity reversal = invocation.getArgument(0);
            reversal.setId(REVERSAL_VOUCHER_ID);
            return 1;
        });
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);
        ExpenseResponse expected = response();
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        assertThat(service().reverse(EXPENSE_ID)).isSameAs(expected);

        ArgumentCaptor<VoucherEntity> voucherCaptor = ArgumentCaptor.forClass(VoucherEntity.class);
        verify(voucherMapper).insert(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getSourceType()).isEqualTo("EXPENSE_REVERSAL");
        assertThat(voucherCaptor.getValue().getVoucherNo()).isEqualTo("VO-EXPENSE-REV-" + EXPENSE_ID);
        assertThat(voucherCaptor.getValue().getBizDate()).isEqualTo(AUDIT.now().toLocalDate());
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> countCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectCount(countCaptor.capture());
        assertThat(countCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("voucher_id");
        ArgumentCaptor<VoucherEntryEntity> entryCaptor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, org.mockito.Mockito.times(2)).insert(entryCaptor.capture());
        assertThat(entryCaptor.getAllValues()).satisfiesExactly(
                debitReversal -> {
                    assertThat(debitReversal.getDebitAmount()).isEqualByComparingTo("0.00");
                    assertThat(debitReversal.getCreditAmount()).isEqualByComparingTo("12.34");
                    assertThat(debitReversal.getSummary()).isEqualTo("红冲: 差旅费");
                },
                creditReversal -> {
                    assertThat(creditReversal.getDebitAmount()).isEqualByComparingTo("12.34");
                    assertThat(creditReversal.getCreditAmount()).isEqualByComparingTo("0.00");
                    assertThat(creditReversal.getSummary()).isEqualTo("红冲: 银行存款");
                }
        );
    }

    @Test
    void reverseRejectsMissingEntriesBeforeWritingReversalVoucher() {
        ExpenseEntity expense = expense("POSTED");
        VoucherEntity originalVoucher = voucher(VOUCHER_ID, "EXPENSE");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseQueryService.findExpenseVoucher(expense)).thenReturn(originalVoucher);
        when(expenseQueryService.voucherEntries(VOUCHER_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().reverse(EXPENSE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("费用单原始凭证缺少分录，无法红冲");

        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
    }

    private ExpensePostingService service() {
        return new ExpensePostingService(
                expenseMapper,
                voucherMapper,
                voucherEntryMapper,
                accountSubjectService,
                auditMetadataFactory,
                expenseQueryService,
                accountPeriodGuard,
                attachmentService
        );
    }

    private ExpenseEntity expense(String status) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(EXPENSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setExpenseNo("EXP-001");
        entity.setExpenseDate(LocalDate.of(2026, 8, 12));
        entity.setSubjectId(9301L);
        entity.setPaymentSubjectId(9302L);
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setRemark("差旅费");
        entity.setVersion(0);
        return entity;
    }

    private AccountSubjectEntity subject(Long id, String code, String name) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setSubjectCode(code);
        entity.setSubjectName(name);
        return entity;
    }

    private VoucherEntity voucher(Long id, String sourceType) {
        VoucherEntity entity = new VoucherEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setVoucherNo("VO-EXPENSE-" + EXPENSE_ID);
        entity.setSourceType(sourceType);
        entity.setSourceId(EXPENSE_ID);
        entity.setSourceNo("EXP-001");
        entity.setBizDate(LocalDate.of(2026, 8, 12));
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private VoucherEntryEntity entry(int lineNo, String debit, String credit, String summary) {
        VoucherEntryEntity entity = new VoucherEntryEntity();
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setVoucherId(VOUCHER_ID);
        entity.setBizDate(LocalDate.of(2026, 8, 12));
        entity.setLineNo(lineNo);
        entity.setSubjectId(9300L + lineNo);
        entity.setSubjectCode("SUB-" + lineNo);
        entity.setSubjectName("科目" + lineNo);
        entity.setDebitAmount(new BigDecimal(debit));
        entity.setCreditAmount(new BigDecimal(credit));
        entity.setSummary(summary);
        return entity;
    }

    private ExpenseResponse response() {
        return new ExpenseResponse(
                EXPENSE_ID,
                "EXP-001",
                LocalDate.of(2026, 8, 12),
                9301L,
                9302L,
                new BigDecimal("12.34"),
                "POSTED",
                VOUCHER_ID,
                "VO-EXPENSE-" + EXPENSE_ID,
                "POSTED",
                new BigDecimal("12.34"),
                2L,
                true,
                true,
                null,
                null,
                null,
                null,
                0L,
                false,
                false,
                false,
                "差旅费"
        );
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
