package com.tuowei.erp.finance.cashflow.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowVoucherEntryMapper;
import com.tuowei.erp.finance.cashflow.model.CashFlowDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowItemDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowVoucherEntry;
import com.tuowei.erp.finance.cashflow.web.CashFlowLineResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowQuery;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CashFlowService {

    private final CashFlowConfigurationService configurationService;
    private final CashFlowVoucherEntryMapper voucherEntryMapper;
    private final TrialBalanceService trialBalanceService;
    private final AuditMetadataFactory auditMetadataFactory;

    public CashFlowService(
            CashFlowConfigurationService configurationService,
            CashFlowVoucherEntryMapper voucherEntryMapper,
            TrialBalanceService trialBalanceService,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.configurationService = configurationService;
        this.voucherEntryMapper = voucherEntryMapper;
        this.trialBalanceService = trialBalanceService;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(CashFlowQuery query) {
        validate(query);
        CashFlowDefinition definition = configurationService.definition();
        AuditMetadata audit = auditMetadataFactory.current();
        List<CashFlowVoucherEntry> entries = definition.cashSubjectCodes().isEmpty()
                ? List.of()
                : voucherEntryMapper.selectCashVoucherEntries(
                        audit.companyId(), audit.accountBookId(), query.getDateFrom(), query.getDateTo(),
                        definition.cashSubjectCodes().stream().sorted().toList()
                );

        Map<String, BigDecimal> amountsByItem = new LinkedHashMap<>();
        definition.items().forEach(item -> amountsByItem.put(item.itemCode(), zero()));
        Set<String> unmappedSubjectCodes = new LinkedHashSet<>();
        for (List<CashFlowVoucherEntry> voucherEntries : groupByVoucher(entries).values()) {
            BigDecimal cashNet = amount(voucherEntries.stream()
                    .filter(entry -> definition.cashSubjectCodes().contains(entry.subjectCode()))
                    .map(entry -> amount(entry.debitAmount()).subtract(amount(entry.creditAmount())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            if (cashNet.signum() == 0) {
                continue;
            }
            for (CashFlowVoucherEntry entry : voucherEntries) {
                if (definition.cashSubjectCodes().contains(entry.subjectCode())) {
                    continue;
                }
                BigDecimal contribution = amount(amount(entry.creditAmount()).subtract(amount(entry.debitAmount())));
                if (contribution.signum() == 0) {
                    continue;
                }
                String itemCode = definition.subjectItemCodes().get(entry.subjectCode());
                if (itemCode == null) {
                    unmappedSubjectCodes.add(entry.subjectCode());
                    continue;
                }
                amountsByItem.compute(itemCode, (ignored, current) -> amount(current.add(contribution)));
            }
        }

        List<CashFlowLineResponse> allLines = definition.items().stream()
                .map(item -> line(item, amountsByItem.get(item.itemCode())))
                .toList();
        List<CashFlowLineResponse> operating = section(allLines, "OPERATING");
        List<CashFlowLineResponse> investing = section(allLines, "INVESTING");
        List<CashFlowLineResponse> financing = section(allLines, "FINANCING");
        BigDecimal netOperating = sum(operating);
        BigDecimal netInvesting = sum(investing);
        BigDecimal netFinancing = sum(financing);
        BigDecimal netIncrease = amount(netOperating.add(netInvesting).add(netFinancing));

        TrialBalanceResponse trial = trialBalanceService.trialBalance(trialQuery(query));
        BigDecimal openingCash = cashBalance(trial, definition.cashSubjectCodes(), true);
        BigDecimal closingCash = cashBalance(trial, definition.cashSubjectCodes(), false);
        BigDecimal difference = amount(openingCash.add(netIncrease).subtract(closingCash));
        List<String> unmapped = unmappedSubjectCodes.stream().sorted().toList();
        List<String> mappingErrors = definition.validationErrors();

        return new CashFlowResponse(
                query.getDateFrom(), query.getDateTo(), definition.formulaVersion(),
                operating, investing, financing,
                netOperating, netInvesting, netFinancing, netIncrease,
                openingCash, closingCash, difference,
                difference.signum() == 0 && unmapped.isEmpty() && mappingErrors.isEmpty(),
                unmapped, mappingErrors
        );
    }

    private Map<Long, List<CashFlowVoucherEntry>> groupByVoucher(List<CashFlowVoucherEntry> entries) {
        Map<Long, List<CashFlowVoucherEntry>> grouped = new LinkedHashMap<>();
        for (CashFlowVoucherEntry entry : entries) {
            grouped.computeIfAbsent(entry.voucherId(), ignored -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private CashFlowLineResponse line(CashFlowItemDefinition item, BigDecimal value) {
        return new CashFlowLineResponse(
                item.itemCode(), item.itemName(), item.activityType(), amount(value)
        );
    }

    private List<CashFlowLineResponse> section(List<CashFlowLineResponse> lines, String activityType) {
        return lines.stream().filter(line -> activityType.equals(line.activityType())).toList();
    }

    private BigDecimal sum(List<CashFlowLineResponse> lines) {
        return amount(lines.stream()
                .map(CashFlowLineResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal cashBalance(TrialBalanceResponse trial, Set<String> cashSubjectCodes, boolean opening) {
        return amount(trial.rows().stream()
                .filter(row -> cashSubjectCodes.contains(row.subjectCode()))
                .map(row -> opening
                        ? amount(row.openingDebit()).subtract(amount(row.openingCredit()))
                        : amount(row.closingDebit()).subtract(amount(row.closingCredit())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private TrialBalanceQuery trialQuery(CashFlowQuery cashFlowQuery) {
        TrialBalanceQuery query = new TrialBalanceQuery();
        query.setDateFrom(cashFlowQuery.getDateFrom());
        query.setDateTo(cashFlowQuery.getDateTo());
        return query;
    }

    private BigDecimal amount(BigDecimal value) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(value));
    }

    private BigDecimal zero() {
        return amount(BigDecimal.ZERO);
    }

    private void validate(CashFlowQuery query) {
        if (query == null || query.getDateFrom() == null || query.getDateTo() == null) {
            throw new IllegalArgumentException("现金流量表日期范围不能为空");
        }
        if (query.getDateFrom().isAfter(query.getDateTo())) {
            throw new IllegalArgumentException("现金流量表开始日期不能晚于结束日期");
        }
    }
}
