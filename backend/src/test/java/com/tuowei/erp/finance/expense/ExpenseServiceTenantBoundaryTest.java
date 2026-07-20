package com.tuowei.erp.finance.expense;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.service.ExpenseNumberService;
import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.workflow.service.WorkflowService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ExpenseServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9701L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 9, 1, 0)
    );
    private static final Long EXPENSE_ID = 9101L;
    private static final Long VOUCHER_ID = 9201L;
    private static final Long SUBJECT_ID = 9301L;
    private static final Long PAYMENT_SUBJECT_ID = 9302L;

    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExpenseEntity.class);
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void listScopesByCompanyAndAccountBook() {
        stubAudit();
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        Page<ExpenseEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(expenseMapper.selectPage(any(Page.class), any())).thenReturn(page);

        service(expenseMapper, mock(VoucherMapper.class), mock(VoucherEntryMapper.class), mock(AccountSubjectService.class))
                .list(new ExpensePageQuery());

        ArgumentCaptor<LambdaQueryWrapper<ExpenseEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(expenseMapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void detailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense(999L, null));

        assertThatThrownBy(() -> service(expenseMapper, mock(VoucherMapper.class), mock(VoucherEntryMapper.class), mock(AccountSubjectService.class))
                .detail(EXPENSE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("费用单不存在");
    }

    @Test
    void createRejectsSubjectFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        AccountSubjectService accountSubjectService = mock(AccountSubjectService.class);
        when(accountSubjectService.requireActiveSubject(SUBJECT_ID, "费用科目不存在或已停用"))
                .thenReturn(subject(SUBJECT_ID, 999L));
        when(accountSubjectService.requireActiveSubject(PAYMENT_SUBJECT_ID, "支付科目不存在或已停用"))
                .thenReturn(subject(PAYMENT_SUBJECT_ID, AUDIT.accountBookId()));

        assertThatThrownBy(() -> service(mock(ExpenseMapper.class), mock(VoucherMapper.class), mock(VoucherEntryMapper.class), accountSubjectService)
                .create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目不存在");
    }

    @Test
    void detailIgnoresLinkedVoucherFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        VoucherMapper voucherMapper = mock(VoucherMapper.class);
        VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense(AUDIT.accountBookId(), VOUCHER_ID));
        when(voucherMapper.selectById(VOUCHER_ID)).thenReturn(voucher(999L, "EXPENSE"));
        when(voucherMapper.selectOne(any())).thenReturn(null);

        var response = service(expenseMapper, voucherMapper, voucherEntryMapper, mock(AccountSubjectService.class))
                .detail(EXPENSE_ID);

        assertThat(response.voucherId()).isNull();
        assertThat(response.voucherNo()).isNull();
        verify(voucherEntryMapper, never()).selectList(any());
    }

    @Test
    void voucherEntryQueriesIncludeCompanyAndAccountBook() {
        stubAudit();
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        VoucherMapper voucherMapper = mock(VoucherMapper.class);
        VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense(AUDIT.accountBookId(), VOUCHER_ID));
        when(voucherMapper.selectById(VOUCHER_ID)).thenReturn(voucher(AUDIT.accountBookId(), "EXPENSE"));
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of());

        service(expenseMapper, voucherMapper, voucherEntryMapper, mock(AccountSubjectService.class))
                .detail(EXPENSE_ID);

        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private ExpenseService service(
            ExpenseMapper expenseMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectService accountSubjectService
    ) {
        return new ExpenseService(
                expenseMapper,
                voucherMapper,
                voucherEntryMapper,
                mock(ExpenseNumberService.class),
                accountSubjectService,
                auditMetadataFactory,
                mock(VoucherQueryService.class),
                mock(AccountPeriodGuard.class),
                mock(AttachmentService.class),
                mock(WorkflowService.class)
        );
    }

    private ExpenseEntity expense(Long accountBookId, Long voucherId) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(EXPENSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setExpenseNo("EXP-001");
        entity.setExpenseDate(LocalDate.of(2026, 6, 9));
        entity.setSubjectId(SUBJECT_ID);
        entity.setPaymentSubjectId(PAYMENT_SUBJECT_ID);
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus("POSTED");
        entity.setVoucherId(voucherId);
        entity.setDeletedFlag(0);
        return entity;
    }

    private VoucherEntity voucher(Long accountBookId, String sourceType) {
        VoucherEntity entity = new VoucherEntity();
        entity.setId(VOUCHER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setVoucherNo("VO-001");
        entity.setSourceType(sourceType);
        entity.setSourceId(EXPENSE_ID);
        entity.setBizDate(LocalDate.of(2026, 6, 9));
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private AccountSubjectEntity subject(Long id, Long accountBookId) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setSubjectCode("6601");
        entity.setSubjectName("费用");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ExpenseCreateRequest createRequest() {
        return new ExpenseCreateRequest(
                LocalDate.of(2026, 6, 9),
                SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                new BigDecimal("12.34"),
                "tenant expense"
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
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
