package com.tuowei.erp.finance.period;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationItemResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationItemResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.period.mapper.PeriodFinancialReportSnapshotMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.model.PeriodFinancialReportSnapshotEntity;
import com.tuowei.erp.finance.period.service.PeriodFinancialReportSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeriodFinancialReportSnapshotServiceTest {

    private final PeriodFinancialReportSnapshotMapper mapper = mock(PeriodFinancialReportSnapshotMapper.class);
    private final TrialBalanceService trialBalanceService = mock(TrialBalanceService.class);
    private final FinancialStatementService statementService = mock(FinancialStatementService.class);
    private final FinancialStatementConfigurationService statementConfigurationService =
            mock(FinancialStatementConfigurationService.class);
    private final CashFlowService cashFlowService = mock(CashFlowService.class);
    private final CashFlowConfigurationService cashFlowConfigurationService =
            mock(CashFlowConfigurationService.class);
    private PeriodFinancialReportSnapshotService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new PeriodFinancialReportSnapshotService(
                mapper,
                trialBalanceService,
                statementService,
                statementConfigurationService,
                cashFlowService,
                cashFlowConfigurationService,
                objectMapper
        );
        stubVerifiedReports();
    }

    @Test
    void capturesFourImmutableReportsWithConfigurationAndPayloadDigests() {
        AccountPeriodEntity period = period();
        AuditMetadata audit = new AuditMetadata(7L, 1L, 1L, LocalDateTime.of(2026, 11, 11, 10, 0));

        service.capture(91L, period, audit);

        ArgumentCaptor<PeriodFinancialReportSnapshotEntity> captor =
                ArgumentCaptor.forClass(PeriodFinancialReportSnapshotEntity.class);
        verify(mapper, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PeriodFinancialReportSnapshotEntity::getReportType)
                .containsExactly("TRIAL_BALANCE", "BALANCE_SHEET", "INCOME_STATEMENT", "CASH_FLOW_DIRECT");
        assertThat(captor.getAllValues()).allSatisfy(snapshot -> {
            assertThat(snapshot.getCloseSnapshotId()).isEqualTo(91L);
            assertThat(snapshot.getPeriodId()).isEqualTo(period.getId());
            assertThat(snapshot.getVerifiedFlag()).isEqualTo(1);
            assertThat(snapshot.getConfigurationDigest()).hasSize(64);
            assertThat(snapshot.getPayloadSha256()).hasSize(64);
            assertThat(snapshot.getConfigurationPayload()).isNotBlank();
            assertThat(snapshot.getReportPayload()).isNotBlank();
        });
        assertThat(captor.getAllValues())
                .filteredOn(snapshot -> snapshot.getReportType().equals("BALANCE_SHEET"))
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getConfigurationPayload())
                        .contains("BS_CASH_BANK")
                        .contains("1002"));
    }

    @Test
    void configurationDigestChangesWhenSubjectMappingsChange() {
        AccountPeriodEntity period = period();
        String before = service.prepare(period).reports().stream()
                .filter(report -> report.reportType().equals("BALANCE_SHEET"))
                .findFirst().orElseThrow().configurationDigest();

        when(statementConfigurationService.configuration("BALANCE_SHEET"))
                .thenReturn(statementConfiguration("BALANCE_SHEET", List.of("1002", "1001")));

        String after = service.prepare(period).reports().stream()
                .filter(report -> report.reportType().equals("BALANCE_SHEET"))
                .findFirst().orElseThrow().configurationDigest();

        assertThat(after).hasSize(64).isNotEqualTo(before);
    }

    @Test
    void refusesToCaptureAnUnreconciledReportSet() {
        CashFlowResponse invalidCashFlow = cashFlow(false);
        when(cashFlowService.cashFlow(any())).thenReturn(invalidCashFlow);

        assertThatThrownBy(() -> service.capture(
                91L,
                period(),
                new AuditMetadata(7L, 1L, 1L, LocalDateTime.of(2026, 11, 11, 10, 0))))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("财务报表核对未通过，不能结账");
    }

    @Test
    void requiresExactlyOneSnapshotForEachFinancialReportType() {
        PeriodFinancialReportSnapshotService.PreparedReports empty =
                new PeriodFinancialReportSnapshotService.PreparedReports(List.of());
        assertThat(empty.verified()).isFalse();
        assertThat(empty.failedReportTypes()).containsExactly(
                "TRIAL_BALANCE", "BALANCE_SHEET", "INCOME_STATEMENT", "CASH_FLOW_DIRECT"
        );

        PeriodFinancialReportSnapshotService.PreparedReport trial = service.prepare(period()).reports().get(0);
        PeriodFinancialReportSnapshotService.PreparedReports duplicates =
                new PeriodFinancialReportSnapshotService.PreparedReports(List.of(trial, trial));
        assertThat(duplicates.verified()).isFalse();
        assertThat(duplicates.failedReportTypes()).containsExactly(
                "TRIAL_BALANCE", "BALANCE_SHEET", "INCOME_STATEMENT", "CASH_FLOW_DIRECT"
        );
    }

    private void stubVerifiedReports() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        BigDecimal zero = BigDecimal.ZERO;
        when(trialBalanceService.trialBalance(any())).thenReturn(new TrialBalanceResponse(
                start, end, List.of(), zero, zero, zero, zero, zero, zero, true, true, true));
        when(statementService.balanceSheet(any())).thenReturn(new BalanceSheetResponse(
                end, "TYPE_MAPPING_V1", List.of(), List.of(), List.of(),
                zero, zero, zero, zero, zero, true, List.of(), List.of()));
        when(statementService.incomeStatement(any())).thenReturn(new IncomeStatementResponse(
                start, end, "TYPE_MAPPING_V1", List.of(), List.of(), zero, zero, zero, List.of(), List.of()));
        when(cashFlowService.cashFlow(any())).thenReturn(cashFlow(true));
        when(statementConfigurationService.configuration("BALANCE_SHEET"))
                .thenReturn(statementConfiguration("BALANCE_SHEET", List.of("1002")));
        when(statementConfigurationService.configuration("INCOME_STATEMENT"))
                .thenReturn(statementConfiguration("INCOME_STATEMENT", List.of("6602")));
        when(cashFlowConfigurationService.configuration()).thenReturn(new CashFlowConfigurationResponse(
                "CASH_FLOW_DIRECT_V1",
                List.of("1002"),
                List.of(new CashFlowConfigurationItemResponse(
                        "CFO_OPERATING_EXPENSES", "支付的其他经营活动现金", "OPERATING", 40, List.of("6602"))),
                List.of()
        ));
    }

    private FinancialStatementConfigurationResponse statementConfiguration(String type, List<String> subjects) {
        return new FinancialStatementConfigurationResponse(
                "TYPE_MAPPING_V1",
                type,
                List.of(new FinancialStatementConfigurationItemResponse(
                        "BS_CASH_BANK", "货币资金", "ASSET", "DEBIT", null, 10, subjects)),
                List.of()
        );
    }

    private CashFlowResponse cashFlow(boolean reconciled) {
        BigDecimal zero = BigDecimal.ZERO;
        return new CashFlowResponse(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "CASH_FLOW_DIRECT_V1",
                List.of(), List.of(), List.of(),
                zero, zero, zero, zero, zero, zero, zero,
                reconciled, List.of(), List.of()
        );
    }

    private AccountPeriodEntity period() {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(81L);
        period.setCompanyId(1L);
        period.setAccountBookId(1L);
        period.setPeriodMonth("2026-10");
        period.setStartDate(LocalDate.of(2026, 10, 1));
        period.setEndDate(LocalDate.of(2026, 10, 31));
        period.setStatus("LOCKED");
        return period;
    }
}
