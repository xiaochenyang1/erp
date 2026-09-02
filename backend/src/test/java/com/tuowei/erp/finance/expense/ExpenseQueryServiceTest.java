package com.tuowei.erp.finance.expense;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import com.tuowei.erp.finance.voucher.web.VoucherResponse;
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
class ExpenseQueryServiceTest {

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
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final VoucherQueryService voucherQueryService = mock(VoucherQueryService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExpenseEntity.class);
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesTenant() {
        stubAudit();
        Page<ExpenseEntity> resultPage = new Page<>(1, 200);
        resultPage.setRecords(List.of());
        resultPage.setTotal(0L);
        when(expenseMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);
        ExpensePageQuery query = new ExpensePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setStatus(" posted ");
        query.setDateFrom(LocalDate.of(2026, 8, 1));
        query.setDateTo(LocalDate.of(2026, 8, 31));

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        ArgumentCaptor<Page<ExpenseEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<ExpenseEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(expenseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("status")
                .contains("expense_date")
                .contains("order by");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "POSTED");
    }

    @Test
    void listUsesDefaultsAndSkipsVoucherHydrationForEmptyPage() {
        stubAudit();
        when(expenseMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        assertThat(result.records()).isEmpty();
        verify(voucherMapper, never()).selectById(any());
        verify(voucherMapper, never()).selectOne(any());
        verify(voucherEntryMapper, never()).selectList(any());
    }

    @Test
    void detailHydratesOriginalAndReversalVoucherState() {
        stubAudit();
        ExpenseEntity expense = expense();
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense);
        VoucherEntity original = voucher(VOUCHER_ID, "EXPENSE", "VO-EXPENSE-1");
        VoucherEntity reversal = voucher(REVERSAL_VOUCHER_ID, "EXPENSE_REVERSAL", "VO-EXPENSE-REV-1");
        when(voucherMapper.selectById(VOUCHER_ID)).thenReturn(original);
        when(voucherMapper.selectOne(any())).thenReturn(reversal);
        when(voucherEntryMapper.selectList(any()))
                .thenReturn(originalEntries())
                .thenReturn(reversalEntries());

        var response = service().detail(EXPENSE_ID);

        assertThat(response.voucherId()).isEqualTo(VOUCHER_ID);
        assertThat(response.voucherEntryCount()).isEqualTo(2L);
        assertThat(response.voucherBalanced()).isTrue();
        assertThat(response.amountMatched()).isTrue();
        assertThat(response.reversalVoucherId()).isEqualTo(REVERSAL_VOUCHER_ID);
        assertThat(response.reversalVoucherEntryCount()).isEqualTo(2L);
        assertThat(response.reversalVoucherBalanced()).isTrue();
        assertThat(response.reversalAmountMatched()).isTrue();
        assertThat(response.reversed()).isTrue();
    }

    @Test
    void reconciliationReturnsTotalsLinkageAndMappedEntries() {
        stubAudit();
        ExpenseEntity expense = expense();
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense);
        VoucherEntity original = voucher(VOUCHER_ID, "EXPENSE", "VO-EXPENSE-1");
        VoucherEntity reversal = voucher(REVERSAL_VOUCHER_ID, "EXPENSE_REVERSAL", "VO-EXPENSE-REV-1");
        when(voucherMapper.selectById(VOUCHER_ID)).thenReturn(original);
        when(voucherMapper.selectOne(any())).thenReturn(reversal);
        List<VoucherEntryEntity> originalEntries = originalEntries();
        List<VoucherEntryEntity> reversalEntries = reversalEntries();
        when(voucherEntryMapper.selectList(any())).thenReturn(originalEntries).thenReturn(reversalEntries);
        VoucherResponse originalResponse = voucherResponse(original);
        VoucherResponse reversalResponse = voucherResponse(reversal);
        when(voucherQueryService.toResponse(original)).thenReturn(originalResponse);
        when(voucherQueryService.toResponse(reversal)).thenReturn(reversalResponse);
        when(voucherQueryService.toEntryResponse(any())).thenAnswer(invocation -> entryResponse(invocation.getArgument(0)));

        var response = service().reconciliation(EXPENSE_ID);

        assertThat(response.voucher()).isSameAs(originalResponse);
        assertThat(response.reversalVoucher()).isSameAs(reversalResponse);
        assertThat(response.entries()).hasSize(2);
        assertThat(response.reversalEntries()).hasSize(2);
        assertThat(response.debitTotal()).isEqualByComparingTo("12.34");
        assertThat(response.creditTotal()).isEqualByComparingTo("12.34");
        assertThat(response.reversalDebitTotal()).isEqualByComparingTo("12.34");
        assertThat(response.reversalCreditTotal()).isEqualByComparingTo("12.34");
        assertThat(response.voucherMissing()).isFalse();
        assertThat(response.entriesMissing()).isFalse();
        assertThat(response.voucherBalanced()).isTrue();
        assertThat(response.amountMatched()).isTrue();
        assertThat(response.voucherLinkedToExpense()).isTrue();
        assertThat(response.reversalVoucherBalanced()).isTrue();
        assertThat(response.reversalAmountMatched()).isTrue();
        assertThat(response.reversed()).isTrue();
    }

    @Test
    void requireExpenseRejectsCrossAccountBookRecord() {
        stubAudit();
        ExpenseEntity expense = expense();
        expense.setAccountBookId(999L);
        when(expenseMapper.selectById(EXPENSE_ID)).thenReturn(expense);

        assertThatThrownBy(() -> service().requireExpense(EXPENSE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("费用单不存在");
    }

    private ExpenseQueryService service() {
        return new ExpenseQueryService(
                expenseMapper,
                voucherMapper,
                voucherEntryMapper,
                auditMetadataFactory,
                voucherQueryService
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private ExpenseEntity expense() {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(EXPENSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setExpenseNo("EXP-001");
        entity.setExpenseDate(LocalDate.of(2026, 8, 13));
        entity.setSubjectId(9301L);
        entity.setPaymentSubjectId(9302L);
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus("POSTED");
        entity.setVoucherId(VOUCHER_ID);
        entity.setDeletedFlag(0);
        entity.setRemark("差旅费");
        return entity;
    }

    private VoucherEntity voucher(Long id, String sourceType, String voucherNo) {
        VoucherEntity entity = new VoucherEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setVoucherNo(voucherNo);
        entity.setSourceType(sourceType);
        entity.setSourceId(EXPENSE_ID);
        entity.setSourceNo("EXP-001");
        entity.setBizDate(LocalDate.of(2026, 8, 13));
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private List<VoucherEntryEntity> originalEntries() {
        return List.of(
                entry(1L, VOUCHER_ID, 1, "12.34", "0"),
                entry(2L, VOUCHER_ID, 2, "0", "12.34")
        );
    }

    private List<VoucherEntryEntity> reversalEntries() {
        return List.of(
                entry(3L, REVERSAL_VOUCHER_ID, 1, "0", "12.34"),
                entry(4L, REVERSAL_VOUCHER_ID, 2, "12.34", "0")
        );
    }

    private VoucherEntryEntity entry(Long id, Long voucherId, int lineNo, String debit, String credit) {
        VoucherEntryEntity entity = new VoucherEntryEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setVoucherId(voucherId);
        entity.setBizDate(LocalDate.of(2026, 8, 13));
        entity.setLineNo(lineNo);
        entity.setSubjectId(9300L + lineNo);
        entity.setSubjectCode("660" + lineNo);
        entity.setSubjectName("科目" + lineNo);
        entity.setDebitAmount(new BigDecimal(debit));
        entity.setCreditAmount(new BigDecimal(credit));
        entity.setSummary("摘要" + lineNo);
        return entity;
    }

    private VoucherResponse voucherResponse(VoucherEntity voucher) {
        return new VoucherResponse(
                voucher.getId(),
                voucher.getVoucherNo(),
                voucher.getSourceType(),
                voucher.getSourceId(),
                voucher.getSourceNo(),
                voucher.getBizDate(),
                voucher.getAmount(),
                voucher.getStatus(),
                null,
                voucher.getRemark()
        );
    }

    private VoucherEntryResponse entryResponse(VoucherEntryEntity entry) {
        return new VoucherEntryResponse(
                entry.getId(),
                entry.getVoucherId(),
                entry.getBizDate(),
                entry.getLineNo(),
                entry.getSubjectId(),
                entry.getSubjectCode(),
                entry.getSubjectName(),
                entry.getDebitAmount(),
                entry.getCreditAmount(),
                entry.getSummary()
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
