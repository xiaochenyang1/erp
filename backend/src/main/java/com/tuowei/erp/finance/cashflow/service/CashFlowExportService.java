package com.tuowei.erp.finance.cashflow.service;

import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.finance.cashflow.web.CashFlowLineResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CashFlowExportService {

    private static final List<String> HEADERS = List.of(
            "dateFrom", "dateTo", "formulaVersion", "activityType", "itemCode", "itemName",
            "amount", "reconciled", "validationDetail"
    );

    private final CashFlowService cashFlowService;

    public CashFlowExportService(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    public StreamingResponseBody export(CashFlowQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> {
            CashFlowResponse response = cashFlowService.cashFlow(query);
            List<List<?>> rows = new ArrayList<>();
            addLines(rows, response, response.operatingLines());
            addLines(rows, response, response.investingLines());
            addLines(rows, response, response.financingLines());
            rows.add(row(response, "TOTAL", "NET_INCREASE_IN_CASH", "现金及现金等价物净增加额",
                    response.netIncreaseInCash()));
            rows.add(row(response, "RECONCILIATION", "OPENING_CASH", "期初现金及现金等价物余额",
                    response.openingCash()));
            rows.add(row(response, "RECONCILIATION", "CLOSING_CASH", "期末现金及现金等价物余额",
                    response.closingCash()));
            rows.add(row(response, "RECONCILIATION", "RECONCILIATION_DIFFERENCE", "勾稽差额",
                    response.reconciliationDifference()));
            CsvExport.write(outputStream, HEADERS, rows);
        });
    }

    private void addLines(List<List<?>> rows, CashFlowResponse response, List<CashFlowLineResponse> lines) {
        for (CashFlowLineResponse line : lines) {
            rows.add(row(response, line.activityType(), line.itemCode(), line.itemName(), line.amount()));
        }
    }

    private List<?> row(CashFlowResponse response, String activityType, String itemCode, String itemName, Object amount) {
        return Arrays.asList(
                response.dateFrom(), response.dateTo(), response.formulaVersion(), activityType,
                itemCode, itemName, amount, response.reconciled(), validationDetail(response)
        );
    }

    private String validationDetail(CashFlowResponse response) {
        List<String> details = new ArrayList<>();
        if (!response.unmappedSubjectCodes().isEmpty()) {
            details.add("unmapped=" + String.join("|", response.unmappedSubjectCodes()));
        }
        if (!response.mappingErrors().isEmpty()) {
            details.add("mappingErrors=" + String.join("|", response.mappingErrors()));
        }
        if (response.reconciliationDifference().signum() != 0) {
            details.add("difference=" + response.reconciliationDifference());
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
