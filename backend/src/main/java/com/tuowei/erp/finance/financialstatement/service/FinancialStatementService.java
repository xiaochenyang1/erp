package com.tuowei.erp.finance.financialstatement.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementLineResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FinancialStatementService {

    public static final String FORMULA_VERSION = "TYPE_MAPPING_V1";
    private static final LocalDate LEDGER_EPOCH = LocalDate.of(1000, 1, 1);
    private static final Set<String> KNOWN_TYPES = Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");

    private final TrialBalanceService trialBalanceService;

    public FinancialStatementService(TrialBalanceService trialBalanceService) {
        this.trialBalanceService = trialBalanceService;
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(BalanceSheetQuery query) {
        if (query == null || query.getAsOfDate() == null) {
            throw new IllegalArgumentException("资产负债表报告日不能为空");
        }
        TrialBalanceResponse trial = trialBalanceService.trialBalance(
                trialQuery(LEDGER_EPOCH, query.getAsOfDate())
        );
        List<FinancialStatementLineResponse> assets = subjectLines(trial, "ASSET", true);
        List<FinancialStatementLineResponse> liabilities = subjectLines(trial, "LIABILITY", false);
        List<FinancialStatementLineResponse> equity = new ArrayList<>(subjectLines(trial, "EQUITY", false));

        BigDecimal totalRevenue = totalForType(trial, "REVENUE", false);
        BigDecimal totalExpense = totalForType(trial, "EXPENSE", true);
        BigDecimal cumulativeProfit = amount(totalRevenue.subtract(totalExpense));
        equity.add(new FinancialStatementLineResponse(
                "CURRENT_PROFIT",
                "累计未结转损益",
                "FORMULA",
                null,
                cumulativeProfit
        ));

        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiabilities = sum(liabilities);
        BigDecimal totalEquity = sum(equity);
        BigDecimal liabilitiesAndEquity = amount(totalLiabilities.add(totalEquity));
        BigDecimal difference = amount(totalAssets.subtract(liabilitiesAndEquity));
        List<String> unclassified = unclassifiedBalance(trial);

        return new BalanceSheetResponse(
                query.getAsOfDate(),
                FORMULA_VERSION,
                List.copyOf(assets),
                List.copyOf(liabilities),
                List.copyOf(equity),
                totalAssets,
                totalLiabilities,
                totalEquity,
                liabilitiesAndEquity,
                difference,
                difference.signum() == 0 && unclassified.isEmpty(),
                unclassified
        );
    }

    @Transactional(readOnly = true)
    public IncomeStatementResponse incomeStatement(IncomeStatementQuery query) {
        validateIncomeQuery(query);
        TrialBalanceResponse trial = trialBalanceService.trialBalance(
                trialQuery(query.getDateFrom(), query.getDateTo())
        );
        List<FinancialStatementLineResponse> revenues = subjectLines(trial, "REVENUE", false, true);
        List<FinancialStatementLineResponse> expenses = subjectLines(trial, "EXPENSE", true, true);
        BigDecimal totalRevenue = sum(revenues);
        BigDecimal totalExpense = sum(expenses);

        return new IncomeStatementResponse(
                query.getDateFrom(),
                query.getDateTo(),
                FORMULA_VERSION,
                revenues,
                expenses,
                totalRevenue,
                totalExpense,
                amount(totalRevenue.subtract(totalExpense)),
                unclassifiedIncome(trial)
        );
    }

    private List<FinancialStatementLineResponse> subjectLines(
            TrialBalanceResponse trial,
            String subjectType,
            boolean debitPositive
    ) {
        return subjectLines(trial, subjectType, debitPositive, false);
    }

    private List<FinancialStatementLineResponse> subjectLines(
            TrialBalanceResponse trial,
            String subjectType,
            boolean debitPositive,
            boolean periodOnly
    ) {
        return trial.rows().stream()
                .filter(row -> subjectType.equals(row.subjectType()))
                .map(row -> new FinancialStatementLineResponse(
                        subjectType + "_" + row.subjectCode(),
                        row.subjectName(),
                        "SUBJECT",
                        row.subjectCode(),
                        lineAmount(row, debitPositive, periodOnly)
                ))
                .toList();
    }

    private BigDecimal totalForType(TrialBalanceResponse trial, String subjectType, boolean debitPositive) {
        return amount(trial.rows().stream()
                .filter(row -> subjectType.equals(row.subjectType()))
                .map(row -> lineAmount(row, debitPositive, false))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal lineAmount(TrialBalanceRowResponse row, boolean debitPositive, boolean periodOnly) {
        BigDecimal debit = periodOnly ? row.periodDebit() : row.closingDebit();
        BigDecimal credit = periodOnly ? row.periodCredit() : row.closingCredit();
        return debitPositive ? amount(debit.subtract(credit)) : amount(credit.subtract(debit));
    }

    private List<String> unclassifiedBalance(TrialBalanceResponse trial) {
        return trial.rows().stream()
                .filter(row -> !KNOWN_TYPES.contains(row.subjectType()))
                .filter(row -> row.closingDebit().compareTo(row.closingCredit()) != 0)
                .map(TrialBalanceRowResponse::subjectCode)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> unclassifiedIncome(TrialBalanceResponse trial) {
        return trial.rows().stream()
                .filter(row -> !KNOWN_TYPES.contains(row.subjectType()))
                .filter(row -> row.periodDebit().signum() != 0 || row.periodCredit().signum() != 0)
                .map(TrialBalanceRowResponse::subjectCode)
                .distinct()
                .sorted()
                .toList();
    }

    private BigDecimal sum(List<FinancialStatementLineResponse> lines) {
        return amount(lines.stream()
                .map(FinancialStatementLineResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal amount(BigDecimal value) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(value));
    }

    private TrialBalanceQuery trialQuery(LocalDate dateFrom, LocalDate dateTo) {
        TrialBalanceQuery query = new TrialBalanceQuery();
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        return query;
    }

    private void validateIncomeQuery(IncomeStatementQuery query) {
        if (query == null || query.getDateFrom() == null || query.getDateTo() == null) {
            throw new IllegalArgumentException("利润表日期范围不能为空");
        }
        if (query.getDateFrom().isAfter(query.getDateTo())) {
            throw new IllegalArgumentException("利润表开始日期不能晚于结束日期");
        }
    }
}
