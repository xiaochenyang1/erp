package com.tuowei.erp.finance.financialstatement.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementDefinition;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementItemDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FinancialStatementService {

    private static final LocalDate LEDGER_EPOCH = LocalDate.of(1000, 1, 1);
    private static final Set<String> BALANCE_MAPPED_TYPES = Set.of("ASSET", "LIABILITY", "EQUITY");
    private static final Set<String> INCOME_MAPPED_TYPES = Set.of("REVENUE", "EXPENSE");

    private final TrialBalanceService trialBalanceService;
    private final FinancialStatementConfigurationService configurationService;

    public FinancialStatementService(
            TrialBalanceService trialBalanceService,
            FinancialStatementConfigurationService configurationService
    ) {
        this.trialBalanceService = trialBalanceService;
        this.configurationService = configurationService;
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(BalanceSheetQuery query) {
        if (query == null || query.getAsOfDate() == null) {
            throw new IllegalArgumentException("资产负债表报告日不能为空");
        }
        TrialBalanceResponse trial = trialBalanceService.trialBalance(
                trialQuery(LEDGER_EPOCH, query.getAsOfDate())
        );
        FinancialStatementDefinition definition = configurationService.definition(
                FinancialStatementConfigurationService.BALANCE_SHEET
        );
        BigDecimal totalRevenue = totalForSubjectType(trial, "REVENUE", false, false);
        BigDecimal totalExpense = totalForSubjectType(trial, "EXPENSE", true, false);
        BigDecimal cumulativeProfit = amount(totalRevenue.subtract(totalExpense));
        List<FinancialStatementLineResponse> allLines = configuredLines(definition, trial, false, cumulativeProfit);
        List<FinancialStatementLineResponse> assets = sectionLines(definition, allLines, "ASSET");
        List<FinancialStatementLineResponse> liabilities = sectionLines(definition, allLines, "LIABILITY");
        List<FinancialStatementLineResponse> equity = sectionLines(definition, allLines, "EQUITY");

        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiabilities = sum(liabilities);
        BigDecimal totalEquity = sum(equity);
        BigDecimal liabilitiesAndEquity = amount(totalLiabilities.add(totalEquity));
        BigDecimal difference = amount(totalAssets.subtract(liabilitiesAndEquity));
        List<String> unclassified = unmappedBalance(trial, definition);
        List<String> mappingErrors = definition.validationErrors();

        return new BalanceSheetResponse(
                query.getAsOfDate(),
                definition.formulaVersion(),
                List.copyOf(assets),
                List.copyOf(liabilities),
                List.copyOf(equity),
                totalAssets,
                totalLiabilities,
                totalEquity,
                liabilitiesAndEquity,
                difference,
                difference.signum() == 0 && unclassified.isEmpty() && mappingErrors.isEmpty(),
                unclassified,
                mappingErrors
        );
    }

    @Transactional(readOnly = true)
    public IncomeStatementResponse incomeStatement(IncomeStatementQuery query) {
        validateIncomeQuery(query);
        TrialBalanceResponse trial = trialBalanceService.trialBalance(
                trialQuery(query.getDateFrom(), query.getDateTo())
        );
        FinancialStatementDefinition definition = configurationService.definition(
                FinancialStatementConfigurationService.INCOME_STATEMENT
        );
        List<FinancialStatementLineResponse> allLines = configuredLines(definition, trial, true, BigDecimal.ZERO);
        List<FinancialStatementLineResponse> revenues = sectionLines(definition, allLines, "REVENUE");
        List<FinancialStatementLineResponse> expenses = sectionLines(definition, allLines, "EXPENSE");
        BigDecimal totalRevenue = sum(revenues);
        BigDecimal totalExpense = sum(expenses);

        return new IncomeStatementResponse(
                query.getDateFrom(),
                query.getDateTo(),
                definition.formulaVersion(),
                revenues,
                expenses,
                totalRevenue,
                totalExpense,
                amount(totalRevenue.subtract(totalExpense)),
                unmappedIncome(trial, definition),
                definition.validationErrors()
        );
    }

    private List<FinancialStatementLineResponse> configuredLines(
            FinancialStatementDefinition definition,
            TrialBalanceResponse trial,
            boolean periodOnly,
            BigDecimal cumulativeProfit
    ) {
        Map<String, TrialBalanceRowResponse> rowsBySubject = trial.rows().stream()
                .collect(LinkedHashMap::new, (map, row) -> map.put(row.subjectCode(), row), Map::putAll);
        return definition.items().stream().map(item -> {
            boolean formula = item.formulaCode() != null;
            BigDecimal value = formula
                    ? formulaAmount(item, cumulativeProfit)
                    : amount(item.subjectCodes().stream()
                            .map(rowsBySubject::get)
                            .filter(java.util.Objects::nonNull)
                            .map(row -> lineAmount(row, item.amountDirection(), periodOnly))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
            return new FinancialStatementLineResponse(
                    item.itemCode(), item.itemName(), formula ? "FORMULA" : "SUBJECT_SUM", null, value
            );
        }).toList();
    }

    private List<FinancialStatementLineResponse> sectionLines(
            FinancialStatementDefinition definition,
            List<FinancialStatementLineResponse> lines,
            String sectionCode
    ) {
        Map<String, String> sections = definition.items().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FinancialStatementItemDefinition::itemCode,
                        FinancialStatementItemDefinition::sectionCode
                ));
        return lines.stream().filter(line -> sectionCode.equals(sections.get(line.itemCode()))).toList();
    }

    private BigDecimal formulaAmount(FinancialStatementItemDefinition item, BigDecimal cumulativeProfit) {
        if ("CUMULATIVE_PROFIT".equals(item.formulaCode())) {
            return amount(cumulativeProfit);
        }
        throw new IllegalStateException("不支持的财务报表公式: " + item.formulaCode());
    }

    private BigDecimal totalForSubjectType(
            TrialBalanceResponse trial,
            String subjectType,
            boolean debitPositive,
            boolean periodOnly
    ) {
        return amount(trial.rows().stream()
                .filter(row -> subjectType.equals(row.subjectType()))
                .map(row -> lineAmount(row, debitPositive ? "DEBIT" : "CREDIT", periodOnly))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal lineAmount(TrialBalanceRowResponse row, String amountDirection, boolean periodOnly) {
        BigDecimal debit = periodOnly ? row.periodDebit() : row.closingDebit();
        BigDecimal credit = periodOnly ? row.periodCredit() : row.closingCredit();
        return "DEBIT".equals(amountDirection)
                ? amount(debit.subtract(credit))
                : amount(credit.subtract(debit));
    }

    private List<String> unmappedBalance(
            TrialBalanceResponse trial,
            FinancialStatementDefinition definition
    ) {
        return trial.rows().stream()
                .filter(row -> row.closingDebit().compareTo(row.closingCredit()) != 0)
                .filter(row -> BALANCE_MAPPED_TYPES.contains(row.subjectType())
                        || (!INCOME_MAPPED_TYPES.contains(row.subjectType())))
                .filter(row -> !definition.subjectItemCodes().containsKey(row.subjectCode()))
                .map(TrialBalanceRowResponse::subjectCode)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> unmappedIncome(
            TrialBalanceResponse trial,
            FinancialStatementDefinition definition
    ) {
        return trial.rows().stream()
                .filter(row -> row.periodDebit().signum() != 0 || row.periodCredit().signum() != 0)
                .filter(row -> INCOME_MAPPED_TYPES.contains(row.subjectType())
                        || (!BALANCE_MAPPED_TYPES.contains(row.subjectType())))
                .filter(row -> !definition.subjectItemCodes().containsKey(row.subjectCode()))
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
