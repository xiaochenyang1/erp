package com.tuowei.erp.finance.financialstatement;

import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementDefinition;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementItemDefinition;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialStatementServiceTest {

    private final TrialBalanceService trialBalanceService = mock(TrialBalanceService.class);
    private final FinancialStatementConfigurationService configurationService =
            mock(FinancialStatementConfigurationService.class);

    @Test
    void balanceSheetAddsCumulativeProfitToEquityAndReconciles() {
        when(configurationService.definition(FinancialStatementConfigurationService.BALANCE_SHEET))
                .thenReturn(balanceDefinition());
        when(trialBalanceService.trialBalance(any())).thenReturn(trial(List.of(
                row("1002", "银行存款", "ASSET", "150", "0", "0", "0"),
                row("2202", "应付账款", "LIABILITY", "0", "100", "0", "0"),
                row("6001", "主营业务收入", "REVENUE", "0", "80", "0", "0"),
                row("6602", "管理费用", "EXPENSE", "30", "0", "0", "0")
        )));
        BalanceSheetQuery query = new BalanceSheetQuery();
        query.setAsOfDate(LocalDate.of(2026, 10, 31));

        BalanceSheetResponse response = service().balanceSheet(query);

        assertThat(response.totalAssets()).isEqualByComparingTo("150.00");
        assertThat(response.totalLiabilities()).isEqualByComparingTo("100.00");
        assertThat(response.totalEquity()).isEqualByComparingTo("50.00");
        assertThat(response.balanced()).isTrue();
        assertThat(response.equityLines()).extracting("itemCode").contains("BS_CURRENT_PROFIT");
    }

    @Test
    void incomeStatementUsesOnlyPeriodMovements() {
        when(configurationService.definition(FinancialStatementConfigurationService.INCOME_STATEMENT))
                .thenReturn(incomeDefinition());
        when(trialBalanceService.trialBalance(any())).thenReturn(trial(List.of(
                row("6001", "主营业务收入", "REVENUE", "0", "1000", "20", "200"),
                row("6602", "管理费用", "EXPENSE", "300", "0", "80", "10")
        )));
        IncomeStatementQuery query = new IncomeStatementQuery();
        query.setDateFrom(LocalDate.of(2026, 10, 1));
        query.setDateTo(LocalDate.of(2026, 10, 31));

        IncomeStatementResponse response = service().incomeStatement(query);

        assertThat(response.totalRevenue()).isEqualByComparingTo("180.00");
        assertThat(response.totalExpense()).isEqualByComparingTo("70.00");
        assertThat(response.netProfit()).isEqualByComparingTo("110.00");
        ArgumentCaptor<com.tuowei.erp.finance.ledger.web.TrialBalanceQuery> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.finance.ledger.web.TrialBalanceQuery.class);
        verify(trialBalanceService).trialBalance(captor.capture());
        assertThat(captor.getValue().getDateFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void unknownSubjectTypeBlocksBalanceSheetReconciliation() {
        when(configurationService.definition(FinancialStatementConfigurationService.BALANCE_SHEET))
                .thenReturn(balanceDefinition());
        when(trialBalanceService.trialBalance(any())).thenReturn(trial(List.of(
                row("9999", "未分类", "UNKNOWN", "10", "0", "0", "0")
        )));
        BalanceSheetQuery query = new BalanceSheetQuery();
        query.setAsOfDate(LocalDate.of(2026, 10, 31));

        BalanceSheetResponse response = service().balanceSheet(query);

        assertThat(response.balanced()).isFalse();
        assertThat(response.unclassifiedSubjectCodes()).containsExactly("9999");
    }

    @Test
    void zeroBalanceUnknownSubjectDoesNotBlockBalanceSheet() {
        when(configurationService.definition(FinancialStatementConfigurationService.BALANCE_SHEET))
                .thenReturn(balanceDefinition());
        when(trialBalanceService.trialBalance(any())).thenReturn(trial(List.of(
                row("9999", "历史已结清科目", "UNKNOWN", "0", "0", "10", "10")
        )));
        BalanceSheetQuery query = new BalanceSheetQuery();
        query.setAsOfDate(LocalDate.of(2026, 10, 31));

        BalanceSheetResponse response = service().balanceSheet(query);

        assertThat(response.balanced()).isTrue();
        assertThat(response.unclassifiedSubjectCodes()).isEmpty();
    }

    private FinancialStatementService service() {
        return new FinancialStatementService(trialBalanceService, configurationService);
    }

    private FinancialStatementDefinition balanceDefinition() {
        return new FinancialStatementDefinition(
                "TYPE_MAPPING_V1",
                "BALANCE_SHEET",
                List.of(
                        item("BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, List.of("1002")),
                        item("BS_PAYABLES", "应付及预收款项", "LIABILITY", "CREDIT", null, List.of("2202")),
                        item("BS_CURRENT_PROFIT", "累计未结转损益", "EQUITY", "CREDIT", "CUMULATIVE_PROFIT", List.of())
                ),
                Map.of("1002", "BS_CASH_BANK", "2202", "BS_PAYABLES"),
                List.of()
        );
    }

    private FinancialStatementDefinition incomeDefinition() {
        return new FinancialStatementDefinition(
                "TYPE_MAPPING_V1",
                "INCOME_STATEMENT",
                List.of(
                        item("IS_OPERATING_REVENUE", "主营业务收入", "REVENUE", "CREDIT", null, List.of("6001")),
                        item("IS_ADMIN_EXPENSE", "管理费用", "EXPENSE", "DEBIT", null, List.of("6602"))
                ),
                Map.of("6001", "IS_OPERATING_REVENUE", "6602", "IS_ADMIN_EXPENSE"),
                List.of()
        );
    }

    private FinancialStatementItemDefinition item(
            String code,
            String name,
            String section,
            String direction,
            String formula,
            List<String> subjects
    ) {
        return new FinancialStatementItemDefinition(code, name, section, direction, formula, 10, subjects);
    }

    private TrialBalanceResponse trial(List<TrialBalanceRowResponse> rows) {
        return new TrialBalanceResponse(
                LocalDate.of(1000, 1, 1), LocalDate.of(2026, 10, 31), rows,
                zero(), zero(), zero(), zero(), zero(), zero(), true, true, true
        );
    }

    private TrialBalanceRowResponse row(
            String code,
            String name,
            String type,
            String closingDebit,
            String closingCredit,
            String periodDebit,
            String periodCredit
    ) {
        return new TrialBalanceRowResponse(
                code, name, type, "DEBIT", zero(), zero(),
                amount(periodDebit), amount(periodCredit), amount(closingDebit), amount(closingCredit)
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private BigDecimal zero() {
        return amount("0");
    }
}
