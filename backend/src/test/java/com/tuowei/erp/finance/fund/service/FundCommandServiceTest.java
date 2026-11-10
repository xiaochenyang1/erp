package com.tuowei.erp.finance.fund.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.fund.mapper.BankStatementMapper;
import com.tuowei.erp.finance.fund.mapper.FundAccountMapper;
import com.tuowei.erp.finance.fund.model.BankStatementEntity;
import com.tuowei.erp.finance.fund.model.FundAccountEntity;
import com.tuowei.erp.finance.fund.web.BankStatementCreateRequest;
import com.tuowei.erp.finance.fund.web.BankStatementMatchRequest;
import com.tuowei.erp.finance.fund.web.BankStatementUnmatchRequest;
import com.tuowei.erp.finance.fund.web.FundAccountCreateRequest;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            901L, 902L, 903L, LocalDateTime.of(2026, 8, 20, 10, 0)
    );

    private final FundAccountMapper accountMapper = mock(FundAccountMapper.class);
    private final BankStatementMapper statementMapper = mock(BankStatementMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final com.tuowei.erp.finance.payment.mapper.PaymentMapper paymentMapper = mock(
            com.tuowei.erp.finance.payment.mapper.PaymentMapper.class
    );
    private final FundStatementNumberService numberService = mock(FundStatementNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final FundQueryService queryService = mock(FundQueryService.class);

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
    void createAccountNormalizesValuesAndSetsAuditDefaults() {
        when(accountMapper.insert(any(FundAccountEntity.class))).thenAnswer(invocation -> {
            invocation.<FundAccountEntity>getArgument(0).setId(11L);
            return 1;
        });
        when(queryService.toAccountResponse(any(FundAccountEntity.class)))
                .thenAnswer(invocation -> {
                    FundAccountEntity entity = invocation.getArgument(0);
                    return new com.tuowei.erp.finance.fund.web.FundAccountResponse(
                            entity.getId(), entity.getAccountCode(), entity.getAccountName(), entity.getAccountType(),
                            entity.getBankName(), entity.getBankAccountNo(), entity.getCurrencyCode(),
                            entity.getOpeningBalance(), entity.getStatus(), entity.getRemark(), entity.getCreatedTime()
                    );
                });

        var response = service().createAccount(new FundAccountCreateRequest(
                " main ", " 主账户 ", " bank ", " 银行 ", " 001 ", null,
                new BigDecimal("12.345"), " 备注 "
        ));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.accountCode()).isEqualTo("MAIN");
        assertThat(response.accountName()).isEqualTo("主账户");
        assertThat(response.accountType()).isEqualTo("BANK");
        assertThat(response.currencyCode()).isEqualTo("CNY");
        assertThat(response.openingBalance()).isEqualByComparingTo("12.35");
        var captor = org.mockito.ArgumentCaptor.forClass(FundAccountEntity.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(captor.getValue().getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(captor.getValue().getDeletedFlag()).isZero();
        assertThat(captor.getValue().getVersion()).isZero();
    }

    @Test
    void createStatementRejectsDisabledAccountBeforeNumberOrInsert() {
        FundAccountEntity account = account();
        account.setStatus("DISABLED");
        when(queryService.requireAccount(11L, AUDIT)).thenReturn(account);

        assertThatThrownBy(() -> service().createStatement(new BankStatementCreateRequest(
                11L, null, LocalDate.of(2026, 8, 20), "IN", new BigDecimal("10"), null, "摘要", null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("资金账户已停用，不能录入流水");
        verify(numberService, never()).nextStatementNo(any());
        verify(statementMapper, never()).insert(any(BankStatementEntity.class));
    }

    @Test
    void matchReceiptChecksDirectionAmountAndUpdatesStatement() {
        BankStatementEntity statement = statement();
        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setId(88L);
        receipt.setCompanyId(AUDIT.companyId());
        receipt.setAccountBookId(AUDIT.accountBookId());
        receipt.setDeletedFlag(0);
        receipt.setStatus("POSTED");
        receipt.setReceiptNo("RC-88");
        receipt.setAmount(new BigDecimal("10.00"));
        when(queryService.requireStatement(21L, AUDIT)).thenReturn(statement);
        when(receiptMapper.selectById(88L)).thenReturn(receipt);
        when(statementMapper.selectCount(any())).thenReturn(0L);
        when(statementMapper.updateById(statement)).thenReturn(1);
        when(queryService.toStatementResponse(any(BankStatementEntity.class)))
                .thenReturn(new com.tuowei.erp.finance.fund.web.BankStatementResponse(
                        21L, 11L, "ST-21", null, statement.getTransactionDate(), "IN", statement.getAmount(),
                        null, "摘要", "MATCHED", "RECEIPT", 88L, "RC-88", AUDIT.now(), AUDIT.userId(), null, null, AUDIT.now()
                ));

        var response = service().matchStatement(21L, new BankStatementMatchRequest(" receipt ", 88L, "备注"));

        assertThat(response.status()).isEqualTo("MATCHED");
        assertThat(statement.getMatchedBizType()).isEqualTo("RECEIPT");
        assertThat(statement.getMatchedBizId()).isEqualTo(88L);
        assertThat(statement.getMatchedBizNo()).isEqualTo("RC-88");
        verify(statementMapper).updateById(statement);
    }

    @Test
    void unmatchUsesVersionScopedUpdateAndReturnsFreshStatement() {
        BankStatementEntity statement = statement();
        statement.setStatus("MATCHED");
        statement.setVersion(3);
        when(queryService.requireStatement(21L, AUDIT)).thenReturn(statement);
        when(statementMapper.update(any(), any())).thenReturn(1);
        when(queryService.toStatementResponse(any(BankStatementEntity.class)))
                .thenReturn(new com.tuowei.erp.finance.fund.web.BankStatementResponse(
                        21L, 11L, "ST-21", null, statement.getTransactionDate(), "IN", statement.getAmount(),
                        null, "摘要", "UNMATCHED", null, null, null, null, null, "原因", null, AUDIT.now()
                ));

        var response = service().unmatchStatement(21L, new BankStatementUnmatchRequest(" 原因 "));

        assertThat(response.status()).isEqualTo("UNMATCHED");
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(statementMapper).update(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertThat(sql).contains("company_id", "account_book_id", "status", "version");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "MATCHED", 3);
    }

    private FundCommandService service() {
        return new FundCommandService(accountMapper, statementMapper, receiptMapper, paymentMapper,
                numberService, auditMetadataFactory, queryService);
    }

    private FundAccountEntity account() {
        FundAccountEntity entity = new FundAccountEntity();
        entity.setId(11L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ENABLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private BankStatementEntity statement() {
        BankStatementEntity entity = new BankStatementEntity();
        entity.setId(21L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setFundAccountId(11L);
        entity.setStatementNo("ST-21");
        entity.setTransactionDate(LocalDate.of(2026, 8, 20));
        entity.setDirection("IN");
        entity.setAmount(new BigDecimal("10.00"));
        entity.setSummary("摘要");
        entity.setStatus("UNMATCHED");
        entity.setDeletedFlag(0);
        entity.setVersion(1);
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
