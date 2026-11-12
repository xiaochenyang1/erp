package com.tuowei.erp.finance.financialstatement;

import com.tuowei.erp.finance.financialstatement.service.FinancialStatementExportService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementLineResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialStatementExportServiceTest {

    private final TrialBalanceService trialBalanceService = mock(TrialBalanceService.class);
    private final FinancialStatementService financialStatementService = mock(FinancialStatementService.class);

    @Test
    void exportsTrialBalanceWithAllThreeBalanceGroups() throws Exception {
        when(trialBalanceService.trialBalance(any())).thenReturn(new TrialBalanceResponse(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                List.of(new TrialBalanceRowResponse(
                        "1002", "银行存款", "ASSET", "DEBIT",
                        amount("10"), amount("0"), amount("20"), amount("5"), amount("25"), amount("0")
                )),
                amount("10"), amount("10"), amount("20"), amount("20"), amount("25"), amount("25"),
                true, true, true
        ));

        String csv = write(service().exportTrialBalance(new TrialBalanceQuery()));

        assertThat(csv).contains("openingDebit,openingCredit,periodDebit,periodCredit,closingDebit,closingCredit")
                .contains("1002,银行存款,ASSET,DEBIT,10.00,0.00,20.00,5.00,25.00,0.00");
    }

    @Test
    void exportsBalanceSheetWithValidationEvidence() throws Exception {
        when(financialStatementService.balanceSheet(any())).thenReturn(new BalanceSheetResponse(
                LocalDate.of(2026, 10, 31), "TYPE_MAPPING_V1",
                List.of(new FinancialStatementLineResponse(
                        "BS_CASH_BANK", "货币资金", "SUBJECT_SUM", null, amount("100")
                )),
                List.of(), List.of(), amount("100"), amount("0"), amount("0"), amount("0"), amount("100"),
                false, List.of("1999"), List.of("科目类别不匹配")
        ));
        BalanceSheetQuery query = new BalanceSheetQuery();
        query.setAsOfDate(LocalDate.of(2026, 10, 31));

        String csv = write(service().exportBalanceSheet(query));

        assertThat(csv).contains("BS_CASH_BANK,货币资金,SUBJECT_SUM,100.00,false")
                .contains("unmapped=1999;mappingErrors=科目类别不匹配")
                .contains("BALANCE_DIFFERENCE,勾稽差额,VALIDATION,100.00,false");
    }

    private FinancialStatementExportService service() {
        return new FinancialStatementExportService(trialBalanceService, financialStatementService);
    }

    private String write(org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
