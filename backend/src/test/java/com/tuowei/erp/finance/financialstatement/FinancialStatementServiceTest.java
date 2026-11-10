package com.tuowei.erp.finance.financialstatement;

import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialStatementServiceTest {

    private final TrialBalanceService trialBalanceService = mock(TrialBalanceService.class);

    @Test
    void balanceSheetAddsCumulativeProfitToEquityAndReconciles() {
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
        assertThat(response.equityLines()).extracting("itemCode").contains("CURRENT_PROFIT");
    }

    @Test
    void incomeStatementUsesOnlyPeriodMovements() {
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
        return new FinancialStatementService(trialBalanceService);
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
