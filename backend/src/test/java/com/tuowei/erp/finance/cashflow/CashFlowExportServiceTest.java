package com.tuowei.erp.finance.cashflow;

import com.tuowei.erp.finance.cashflow.service.CashFlowExportService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowLineResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
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

class CashFlowExportServiceTest {

    private final CashFlowService cashFlowService = mock(CashFlowService.class);

    @Test
    void exportsLinesTotalsAndValidationEvidence() throws Exception {
        when(cashFlowService.cashFlow(any())).thenReturn(new CashFlowResponse(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "CASH_FLOW_DIRECT_V1",
                List.of(new CashFlowLineResponse(
                        "CFO_CUSTOMER_RECEIPTS", "客户收款", "OPERATING", amount("100")
                )),
                List.of(), List.of(),
                amount("100"), amount("0"), amount("0"), amount("100"),
                amount("20"), amount("110"), amount("10"), false,
                List.of("9999"), List.of("重复映射")
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CashFlowExportService(cashFlowService).export(new CashFlowQuery()).writeTo(output);
        String csv = output.toString(StandardCharsets.UTF_8);

        assertThat(csv)
                .contains("CFO_CUSTOMER_RECEIPTS,客户收款,100.00,false")
                .contains("NET_INCREASE_IN_CASH,现金及现金等价物净增加额,100.00,false")
                .contains("unmapped=9999;mappingErrors=重复映射;difference=10.00");
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
