package com.tuowei.erp.finance.cashflow;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowVoucherEntryMapper;
import com.tuowei.erp.finance.cashflow.model.CashFlowDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowItemDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowVoucherEntry;
import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CashFlowServiceTest {

    private final CashFlowConfigurationService configurationService = mock(CashFlowConfigurationService.class);
    private final CashFlowVoucherEntryMapper voucherEntryMapper = mock(CashFlowVoucherEntryMapper.class);
    private final TrialBalanceService trialBalanceService = mock(TrialBalanceService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @Test
    void classifiesDirectCashFlowsExcludesInternalTransfersAndReconciles() {
        when(configurationService.definition()).thenReturn(definition(List.of()));
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(voucherEntryMapper.selectCashVoucherEntries(any(), any(), any(), any(), any())).thenReturn(List.of(
                entry(1L, 1, "1002", "100", "0"),
                entry(1L, 2, "1122", "0", "100"),
                entry(2L, 1, "2202", "60", "0"),
                entry(2L, 2, "1002", "0", "60"),
                entry(3L, 1, "1002", "150", "0"),
                entry(3L, 2, "1122", "0", "100"),
                entry(3L, 3, "6001", "0", "50"),
                entry(4L, 1, "1122", "20", "0"),
                entry(4L, 2, "1002", "0", "20"),
                entry(5L, 1, "1002", "0", "30"),
                entry(5L, 2, "1001", "30", "0")
        ));
        when(trialBalanceService.trialBalance(any())).thenReturn(trial("100", "270"));

        CashFlowResponse response = service().cashFlow(query());

        assertThat(response.operatingLines()).extracting("amount")
                .containsExactly(amount("180"), amount("-60"));
        assertThat(response.netOperatingCashFlow()).isEqualByComparingTo("120.00");
        assertThat(response.netInvestingCashFlow()).isEqualByComparingTo("50.00");
        assertThat(response.netIncreaseInCash()).isEqualByComparingTo("170.00");
        assertThat(response.openingCash()).isEqualByComparingTo("100.00");
        assertThat(response.closingCash()).isEqualByComparingTo("270.00");
        assertThat(response.reconciliationDifference()).isZero();
        assertThat(response.reconciled()).isTrue();
    }

    @Test
    void unmappedCounterpartBlocksReconciliationAndExposesDifference() {
        when(configurationService.definition()).thenReturn(definition(List.of()));
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(voucherEntryMapper.selectCashVoucherEntries(any(), any(), any(), any(), any())).thenReturn(List.of(
                entry(1L, 1, "1002", "10", "0"),
                entry(1L, 2, "9999", "0", "10")
        ));
        when(trialBalanceService.trialBalance(any())).thenReturn(trial("0", "10"));

        CashFlowResponse response = service().cashFlow(query());

        assertThat(response.unmappedSubjectCodes()).containsExactly("9999");
        assertThat(response.reconciliationDifference()).isEqualByComparingTo("-10.00");
        assertThat(response.reconciled()).isFalse();
    }

    @Test
    void mappingErrorsBlockReviewedStateEvenWhenAmountsReconcile() {
        when(configurationService.definition()).thenReturn(definition(List.of("重复映射")));
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(voucherEntryMapper.selectCashVoucherEntries(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(trialBalanceService.trialBalance(any())).thenReturn(trial("0", "0"));

        CashFlowResponse response = service().cashFlow(query());

        assertThat(response.reconciliationDifference()).isZero();
        assertThat(response.mappingErrors()).containsExactly("重复映射");
        assertThat(response.reconciled()).isFalse();
    }

    @Test
    void rejectsInvalidDateRange() {
        CashFlowQuery query = query();
        query.setDateFrom(LocalDate.of(2026, 11, 2));
        query.setDateTo(LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> service().cashFlow(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("开始日期");
    }

    private CashFlowService service() {
        return new CashFlowService(
                configurationService, voucherEntryMapper, trialBalanceService, auditMetadataFactory
        );
    }

    private CashFlowDefinition definition(List<String> errors) {
        return new CashFlowDefinition(
                "CASH_FLOW_DIRECT_V1",
                List.of(
                        new CashFlowItemDefinition(
                                "CFO_CUSTOMER_RECEIPTS", "客户收款", "OPERATING", 10,
                                List.of("1122")
                        ),
                        new CashFlowItemDefinition(
                                "CFO_SUPPLIER_PAYMENTS", "供应商付款", "OPERATING", 20,
                                List.of("2202")
                        ),
                        new CashFlowItemDefinition(
                                "CFI_OTHER", "其他投资活动", "INVESTING", 110,
                                List.of("6001")
                        ),
                        new CashFlowItemDefinition(
                                "CFF_OTHER", "其他筹资活动", "FINANCING", 210,
                                List.of()
                        )
                ),
                Set.of("1001", "1002"),
                Map.of(
                        "1122", "CFO_CUSTOMER_RECEIPTS",
                        "2202", "CFO_SUPPLIER_PAYMENTS",
                        "6001", "CFI_OTHER"
                ),
                errors
        );
    }

    private CashFlowVoucherEntry entry(Long voucherId, int lineNo, String subjectCode, String debit, String credit) {
        return new CashFlowVoucherEntry(
                voucherId, LocalDate.of(2026, 11, 1), lineNo, subjectCode, subjectCode,
                amount(debit), amount(credit)
        );
    }

    private TrialBalanceResponse trial(String openingCash, String closingCash) {
        TrialBalanceRowResponse cash = new TrialBalanceRowResponse(
                "1002", "银行存款", "ASSET", "DEBIT",
                amount(openingCash), amount("0"), amount("0"), amount("0"),
                amount(closingCash), amount("0")
        );
        return new TrialBalanceResponse(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), List.of(cash),
                amount("0"), amount("0"), amount("0"), amount("0"), amount("0"), amount("0"),
                true, true, true
        );
    }

    private CashFlowQuery query() {
        CashFlowQuery query = new CashFlowQuery();
        query.setDateFrom(LocalDate.of(2026, 11, 1));
        query.setDateTo(LocalDate.of(2026, 11, 30));
        return query;
    }

    private AuditMetadata audit() {
        return new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
