package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class OpeningAccountBalanceImportHandlerTenantBoundaryTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9943L;

    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(AccountSubjectEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                AccountSubjectEntity.class.getName()
        );
        assistant.setCurrentNamespace(AccountSubjectEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, AccountSubjectEntity.class);
    }

    @Test
    void validateScopesSubjectAndChildLookupsByCompanyAndAccountBook() {
        when(accountSubjectMapper.selectOne(any())).thenReturn(activeSubject());
        when(accountSubjectMapper.selectCount(any())).thenReturn(0L);

        ImportTypeHandler.ImportRowPlan plan = handler().validate(
                1,
                Map.of(
                        "subject_code", "1001",
                        "biz_date", "2026-06-08",
                        "debit_amount", "10.00",
                        "credit_amount", "0.00"
                ),
                new ImportTypeHandler.ImportValidationContext(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID)
        );

        assertThat(plan.valid()).isTrue();

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> subjectWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper).selectOne(subjectWrapper.capture());
        assertTenantScoped(subjectWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> childWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper).selectCount(childWrapper.capture());
        assertTenantScoped(childWrapper.getValue());
    }

    private OpeningAccountBalanceImportHandler handler() {
        return new OpeningAccountBalanceImportHandler(
                new ImportValidationSupport(new ObjectMapper()),
                accountSubjectMapper,
                voucherMapper,
                voucherEntryMapper
        );
    }

    private AccountSubjectEntity activeSubject() {
        AccountSubjectEntity subject = new AccountSubjectEntity();
        subject.setId(7601L);
        subject.setCompanyId(COMPANY_ID);
        subject.setAccountBookId(ACCOUNT_BOOK_ID);
        subject.setSubjectCode("1001");
        subject.setSubjectName("tenant subject");
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
}
