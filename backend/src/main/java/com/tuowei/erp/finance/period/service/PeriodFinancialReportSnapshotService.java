package com.tuowei.erp.finance.period.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.period.mapper.PeriodFinancialReportSnapshotMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.model.PeriodFinancialReportSnapshotEntity;
import com.tuowei.erp.finance.period.web.PeriodFinancialReportSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PeriodFinancialReportSnapshotService {

    static final String TRIAL_BALANCE = "TRIAL_BALANCE";
    static final String BALANCE_SHEET = "BALANCE_SHEET";
    static final String INCOME_STATEMENT = "INCOME_STATEMENT";
    static final String CASH_FLOW_DIRECT = "CASH_FLOW_DIRECT";
    static final String TRIAL_BALANCE_VERSION = "LEDGER_TRIAL_BALANCE_V1";
    private static final List<String> REQUIRED_REPORT_TYPES = List.of(
            TRIAL_BALANCE, BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW_DIRECT
    );

    private final PeriodFinancialReportSnapshotMapper snapshotMapper;
    private final TrialBalanceService trialBalanceService;
    private final FinancialStatementService financialStatementService;
    private final FinancialStatementConfigurationService statementConfigurationService;
    private final CashFlowService cashFlowService;
    private final CashFlowConfigurationService cashFlowConfigurationService;
    private final ObjectMapper objectMapper;

    public PeriodFinancialReportSnapshotService(
            PeriodFinancialReportSnapshotMapper snapshotMapper,
            TrialBalanceService trialBalanceService,
            FinancialStatementService financialStatementService,
            FinancialStatementConfigurationService statementConfigurationService,
            CashFlowService cashFlowService,
            CashFlowConfigurationService cashFlowConfigurationService,
            ObjectMapper objectMapper
    ) {
        this.snapshotMapper = snapshotMapper;
        this.trialBalanceService = trialBalanceService;
        this.financialStatementService = financialStatementService;
        this.statementConfigurationService = statementConfigurationService;
        this.cashFlowService = cashFlowService;
        this.cashFlowConfigurationService = cashFlowConfigurationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PreparedReports prepare(AccountPeriodEntity period) {
        TrialBalanceResponse trial = trialBalanceService.trialBalance(trialQuery(period));
        BalanceSheetResponse balance = financialStatementService.balanceSheet(balanceQuery(period));
        IncomeStatementResponse income = financialStatementService.incomeStatement(incomeQuery(period));
        CashFlowResponse cashFlow = cashFlowService.cashFlow(cashFlowQuery(period));

        String balanceConfiguration = json(statementConfigurationService.configuration(BALANCE_SHEET));
        String incomeConfiguration = json(statementConfigurationService.configuration(INCOME_STATEMENT));
        String cashConfiguration = json(cashFlowConfigurationService.configuration());

        return new PreparedReports(List.of(
                prepared(TRIAL_BALANCE, TRIAL_BALANCE_VERSION,
                        json(java.util.Map.of("formulaVersion", TRIAL_BALANCE_VERSION)),
                        period.getStartDate(), period.getEndDate(),
                        trial.openingBalanced() && trial.periodBalanced() && trial.closingBalanced(), trial),
                prepared(BALANCE_SHEET, balance.formulaVersion(), balanceConfiguration,
                        period.getEndDate(), period.getEndDate(), balance.balanced(), balance),
                prepared(INCOME_STATEMENT, income.formulaVersion(), incomeConfiguration,
                        period.getStartDate(), period.getEndDate(),
                        income.unclassifiedSubjectCodes().isEmpty() && income.mappingErrors().isEmpty(), income),
                prepared(CASH_FLOW_DIRECT, cashFlow.formulaVersion(), cashConfiguration,
                        period.getStartDate(), period.getEndDate(), cashFlow.reconciled(), cashFlow)
        ));
    }

    @Transactional
    public void capture(Long closeSnapshotId, AccountPeriodEntity period, AuditMetadata audit) {
        PreparedReports preparedReports = prepare(period);
        if (!preparedReports.verified()) {
            throw new BusinessConflictException("财务报表核对未通过，不能结账");
        }
        for (PreparedReport prepared : preparedReports.reports()) {
            PeriodFinancialReportSnapshotEntity entity = new PeriodFinancialReportSnapshotEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setPeriodId(period.getId());
            entity.setCloseSnapshotId(closeSnapshotId);
            entity.setReportType(prepared.reportType());
            entity.setFormulaVersion(prepared.formulaVersion());
            entity.setConfigurationDigest(prepared.configurationDigest());
            entity.setConfigurationPayload(prepared.configurationJson());
            entity.setDateFrom(prepared.dateFrom());
            entity.setDateTo(prepared.dateTo());
            entity.setVerifiedFlag(prepared.verified() ? 1 : 0);
            entity.setReportPayload(prepared.payloadJson());
            entity.setPayloadSha256(prepared.payloadSha256());
            entity.setCapturedBy(audit.userId());
            entity.setCapturedTime(audit.now());
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(audit.now());
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(audit.now());
            entity.setVersion(0);
            snapshotMapper.insert(entity);
        }
    }

    @Transactional(readOnly = true)
    public List<PeriodFinancialReportSnapshotResponse> list(Long closeSnapshotId) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<PeriodFinancialReportSnapshotEntity>()
                        .eq(PeriodFinancialReportSnapshotEntity::getCloseSnapshotId, closeSnapshotId)
                        .orderByAsc(PeriodFinancialReportSnapshotEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PreparedReport prepared(
            String reportType,
            String formulaVersion,
            String configurationJson,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean verified,
            Object payload
    ) {
        String payloadJson = json(payload);
        return new PreparedReport(
                reportType, formulaVersion, sha256(configurationJson), configurationJson, dateFrom, dateTo,
                verified, payloadJson, sha256(payloadJson)
        );
    }

    private PeriodFinancialReportSnapshotResponse toResponse(PeriodFinancialReportSnapshotEntity entity) {
        try {
            JsonNode payload = objectMapper.readTree(entity.getReportPayload());
            JsonNode configuration = objectMapper.readTree(entity.getConfigurationPayload());
            return new PeriodFinancialReportSnapshotResponse(
                    entity.getId(), entity.getReportType(), entity.getFormulaVersion(),
                    entity.getConfigurationDigest(), configuration, entity.getDateFrom(), entity.getDateTo(),
                    Integer.valueOf(1).equals(entity.getVerifiedFlag()), entity.getPayloadSha256(),
                    entity.getCapturedBy(), entity.getCapturedTime(), payload
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("历史财务报表快照损坏: " + entity.getId(), ex);
        }
    }

    private TrialBalanceQuery trialQuery(AccountPeriodEntity period) {
        TrialBalanceQuery query = new TrialBalanceQuery();
        query.setDateFrom(period.getStartDate());
        query.setDateTo(period.getEndDate());
        return query;
    }

    private BalanceSheetQuery balanceQuery(AccountPeriodEntity period) {
        BalanceSheetQuery query = new BalanceSheetQuery();
        query.setAsOfDate(period.getEndDate());
        return query;
    }

    private IncomeStatementQuery incomeQuery(AccountPeriodEntity period) {
        IncomeStatementQuery query = new IncomeStatementQuery();
        query.setDateFrom(period.getStartDate());
        query.setDateTo(period.getEndDate());
        return query;
    }

    private CashFlowQuery cashFlowQuery(AccountPeriodEntity period) {
        CashFlowQuery query = new CashFlowQuery();
        query.setDateFrom(period.getStartDate());
        query.setDateTo(period.getEndDate());
        return query;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("财务报表快照序列化失败", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record PreparedReports(List<PreparedReport> reports) {
        public PreparedReports {
            reports = reports == null ? List.of() : List.copyOf(reports);
        }

        public boolean verified() {
            Set<String> reportTypes = reports.stream()
                    .map(PreparedReport::reportType)
                    .collect(java.util.stream.Collectors.toSet());
            return reports.size() == REQUIRED_REPORT_TYPES.size()
                    && reportTypes.size() == REQUIRED_REPORT_TYPES.size()
                    && reportTypes.containsAll(REQUIRED_REPORT_TYPES)
                    && reports.stream().allMatch(PreparedReport::verified);
        }

        public List<String> failedReportTypes() {
            LinkedHashSet<String> failures = new LinkedHashSet<>();
            for (String requiredType : REQUIRED_REPORT_TYPES) {
                long matches = reports.stream().filter(report -> requiredType.equals(report.reportType())).count();
                if (matches != 1) {
                    failures.add(requiredType);
                }
            }
            reports.stream()
                    .filter(report -> !report.verified())
                    .map(PreparedReport::reportType)
                    .forEach(failures::add);
            return new ArrayList<>(failures);
        }
    }

    public record PreparedReport(
            String reportType,
            String formulaVersion,
            String configurationDigest,
            String configurationJson,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean verified,
            String payloadJson,
            String payloadSha256
    ) {
    }
}
