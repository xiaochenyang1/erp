package com.tuowei.erp.finance.voucher;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherPageQuery;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            1L,
            10L,
            LocalDateTime.of(2026, 6, 8, 10, 0)
    );

    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
    }

    @Test
    void listScopesVoucherQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(voucherMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<VoucherEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new VoucherPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    @Test
    void detailRejectsVoucherFromDifferentAccountBookInSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        VoucherEntity voucher = activeVoucher(1001L, AUDIT.companyId(), 99L);
        when(voucherMapper.selectById(1001L)).thenReturn(voucher);

        assertThatThrownBy(() -> service().detail(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("凭证不存在");
    }

    @Test
    void entriesScopeEntryQueryByVoucherCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        VoucherEntity voucher = activeVoucher(1002L, AUDIT.companyId(), AUDIT.accountBookId());
        when(voucherMapper.selectById(1002L)).thenReturn(voucher);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of());

        service().entries(1002L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("voucher_id");
    }

    @Test
    void toResponseOmitsExpenseSourceFromDifferentAccountBook() {
        VoucherEntity voucher = activeVoucher(1003L, AUDIT.companyId(), AUDIT.accountBookId());
        voucher.setSourceType("EXPENSE");
        voucher.setSourceId(2003L);
        ExpenseEntity expense = new ExpenseEntity();
        expense.setId(2003L);
        expense.setCompanyId(AUDIT.companyId());
        expense.setAccountBookId(99L);
        expense.setExpenseNo("EXP-2003");
        expense.setExpenseDate(LocalDate.of(2026, 6, 8));
        expense.setStatus("POSTED");
        expense.setAmount(new BigDecimal("12.34"));
        expense.setDeletedFlag(0);
        when(expenseMapper.selectById(2003L)).thenReturn(expense);

        assertThat(service().toResponse(voucher).expenseSource()).isNull();
    }

    private VoucherQueryService service() {
        return new VoucherQueryService(
                voucherMapper,
                voucherEntryMapper,
                expenseMapper,
                auditMetadataFactory
        );
    }

    private VoucherEntity activeVoucher(Long id, Long companyId, Long accountBookId) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(companyId);
        voucher.setAccountBookId(accountBookId);
        voucher.setVoucherNo("V-" + id);
        voucher.setSourceType("MANUAL");
        voucher.setDeletedFlag(0);
        return voucher;
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
