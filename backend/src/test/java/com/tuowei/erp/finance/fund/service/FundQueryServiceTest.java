package com.tuowei.erp.finance.fund.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.fund.mapper.BankStatementMapper;
import com.tuowei.erp.finance.fund.mapper.FundAccountMapper;
import com.tuowei.erp.finance.fund.model.BankStatementEntity;
import com.tuowei.erp.finance.fund.model.FundAccountEntity;
import com.tuowei.erp.finance.fund.web.BankStatementPageQuery;
import com.tuowei.erp.finance.fund.web.FundAccountPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class FundQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            901L, 902L, 903L, LocalDateTime.of(2026, 8, 20, 10, 0)
    );

    private final FundAccountMapper accountMapper = mock(FundAccountMapper.class);
    private final BankStatementMapper statementMapper = mock(BankStatementMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(FundAccountEntity.class);
        initTableInfo(BankStatementEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void accountListNormalizesFiltersCapsPageAndScopesTenant() {
        when(accountMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<FundAccountEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(account(11L)));
            return page;
        });
        FundAccountPageQuery query = new FundAccountPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setAccountType(" bank ");
        query.setStatus(" enabled ");
        query.setKeyword("  main ");

        var response = service().listAccounts(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement().extracting(item -> item.accountCode()).isEqualTo("MAIN");
        var pageCaptor = org.mockito.ArgumentCaptor.forClass(Page.class);
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id", "account_book_id", "deleted_flag", "account_type", "status")
                .contains("account_code").contains("account_name");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "BANK", "ENABLED", "%main%");
    }

    @Test
    void statementListNormalizesDirectionAndDateFilters() {
        when(statementMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<BankStatementEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(statement(21L)));
            page.setTotal(1);
            return page;
        });
        BankStatementPageQuery query = new BankStatementPageQuery();
        query.setFundAccountId(11L);
        query.setDirection(" in ");
        query.setStatus(" unmatched ");
        query.setTransactionDateFrom(LocalDate.of(2026, 8, 1));
        query.setTransactionDateTo(LocalDate.of(2026, 8, 31));
        query.setMatchedBizType(" receipt ");
        query.setMatchedBizNo(" R-1 ");

        var response = service().listStatements(query);

        assertThat(response.records()).singleElement().extracting(item -> item.status()).isEqualTo("UNMATCHED");
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(statementMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id", "account_book_id", "deleted_flag", "fund_account_id",
                "direction", "status", "transaction_date", "matched_biz_type", "matched_biz_no");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 11L, "IN", "UNMATCHED", "RECEIPT", "%R-1%");
    }

    @Test
    void detailRejectsCrossTenantAccount() {
        FundAccountEntity entity = account(11L);
        entity.setCompanyId(999L);
        when(accountMapper.selectById(11L)).thenReturn(entity);

        assertThatThrownBy(() -> service().accountDetail(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("资金账户不存在");
    }

    private FundQueryService service() {
        return new FundQueryService(accountMapper, statementMapper, auditMetadataFactory);
    }

    private FundAccountEntity account(Long id) {
        FundAccountEntity entity = new FundAccountEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setAccountCode("MAIN");
        entity.setAccountName("主账户");
        entity.setAccountType("BANK");
        entity.setCurrencyCode("CNY");
        entity.setOpeningBalance(new BigDecimal("12.34"));
        entity.setStatus("ENABLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private BankStatementEntity statement(Long id) {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setFundAccountId(11L);
        entity.setStatementNo("ST-1");
        entity.setTransactionDate(LocalDate.of(2026, 8, 20));
        entity.setDirection("IN");
        entity.setAmount(new BigDecimal("10.00"));
        entity.setSummary("摘要");
        entity.setStatus("UNMATCHED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), type.getName());
        assistant.setCurrentNamespace(type.getName());
        TableInfoHelper.initTableInfo(assistant, type);
    }
}
