package com.tuowei.erp.finance.financialstatement.service;

import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementLineResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class FinancialStatementExportService {

    private static final List<String> TRIAL_BALANCE_HEADERS = List.of(
            "dateFrom", "dateTo", "subjectCode", "subjectName", "subjectType", "balanceDirection",
            "openingDebit", "openingCredit", "periodDebit", "periodCredit", "closingDebit", "closingCredit"
    );
    private static final List<String> STATEMENT_HEADERS = List.of(
            "dateFrom", "dateTo", "formulaVersion", "section", "itemCode", "itemName", "lineType",
            "amount", "validated", "validationDetail"
    );

    private final TrialBalanceService trialBalanceService;
    private final FinancialStatementService financialStatementService;

    public FinancialStatementExportService(
            TrialBalanceService trialBalanceService,
            FinancialStatementService financialStatementService
    ) {
        this.trialBalanceService = trialBalanceService;
        this.financialStatementService = financialStatementService;
    }

    public StreamingResponseBody exportTrialBalance(TrialBalanceQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> {
            TrialBalanceResponse response = trialBalanceService.trialBalance(query);
            List<List<?>> rows = new ArrayList<>();
            for (TrialBalanceRowResponse row : response.rows()) {
                rows.add(trialBalanceRow(response, row));
            }
            CsvExport.write(outputStream, TRIAL_BALANCE_HEADERS, rows);
        });
    }

    public StreamingResponseBody exportBalanceSheet(BalanceSheetQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> {
            BalanceSheetResponse response = financialStatementService.balanceSheet(query);
            boolean validated = response.balanced();
            String validation = validationDetail(response.unclassifiedSubjectCodes(), response.mappingErrors());
            List<List<?>> rows = new ArrayList<>();
            addStatementRows(rows, response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "ASSET", response.assetLines(), validated, validation);
            addStatementRows(rows, response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "LIABILITY", response.liabilityLines(), validated, validation);
            addStatementRows(rows, response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "EQUITY", response.equityLines(), validated, validation);
            rows.add(statementRow(response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "TOTAL", "TOTAL_ASSETS", "资产合计", "TOTAL", response.totalAssets(), validated, validation));
            rows.add(statementRow(response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "TOTAL", "TOTAL_LIABILITIES_EQUITY", "负债及权益合计", "TOTAL",
                    response.liabilitiesAndEquity(), validated, validation));
            rows.add(statementRow(response.asOfDate(), response.asOfDate(), response.formulaVersion(),
                    "TOTAL", "BALANCE_DIFFERENCE", "勾稽差额", "VALIDATION",
                    response.difference(), validated, validation));
            CsvExport.write(outputStream, STATEMENT_HEADERS, rows);
        });
    }

    public StreamingResponseBody exportIncomeStatement(IncomeStatementQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> {
            IncomeStatementResponse response = financialStatementService.incomeStatement(query);
            boolean validated = response.unclassifiedSubjectCodes().isEmpty() && response.mappingErrors().isEmpty();
            String validation = validationDetail(response.unclassifiedSubjectCodes(), response.mappingErrors());
            List<List<?>> rows = new ArrayList<>();
            addStatementRows(rows, response.dateFrom(), response.dateTo(), response.formulaVersion(),
                    "REVENUE", response.revenueLines(), validated, validation);
            addStatementRows(rows, response.dateFrom(), response.dateTo(), response.formulaVersion(),
                    "EXPENSE", response.expenseLines(), validated, validation);
            rows.add(statementRow(response.dateFrom(), response.dateTo(), response.formulaVersion(),
                    "TOTAL", "NET_PROFIT", "净利润", "TOTAL", response.netProfit(), validated, validation));
            CsvExport.write(outputStream, STATEMENT_HEADERS, rows);
        });
    }

    private List<?> trialBalanceRow(TrialBalanceResponse response, TrialBalanceRowResponse row) {
        return Arrays.asList(
                response.dateFrom(), response.dateTo(), row.subjectCode(), row.subjectName(), row.subjectType(),
                row.balanceDirection(), row.openingDebit(), row.openingCredit(), row.periodDebit(),
                row.periodCredit(), row.closingDebit(), row.closingCredit()
        );
    }

    private void addStatementRows(
            List<List<?>> rows,
            Object dateFrom,
            Object dateTo,
            String formulaVersion,
            String section,
            List<FinancialStatementLineResponse> lines,
            boolean validated,
            String validation
    ) {
        for (FinancialStatementLineResponse line : lines) {
            rows.add(statementRow(dateFrom, dateTo, formulaVersion, section, line.itemCode(), line.itemName(),
                    line.lineType(), line.amount(), validated, validation));
        }
    }

    private List<?> statementRow(
            Object dateFrom,
            Object dateTo,
            String formulaVersion,
            String section,
            String itemCode,
            String itemName,
            String lineType,
            Object amount,
            boolean validated,
            String validation
    ) {
        return Arrays.asList(
                dateFrom, dateTo, formulaVersion, section, itemCode, itemName, lineType, amount,
                validated, validation
        );
    }

    private String validationDetail(List<String> unmapped, List<String> mappingErrors) {
        List<String> details = new ArrayList<>();
        if (!unmapped.isEmpty()) {
            details.add("unmapped=" + String.join("|", unmapped));
        }
        if (!mappingErrors.isEmpty()) {
            details.add("mappingErrors=" + String.join("|", mappingErrors));
        }
        return String.join(";", details);
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException;
    }
}
