package com.tuowei.erp.finance.ledger.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.ledger.mapper.TrialBalanceMapper;
import com.tuowei.erp.finance.ledger.model.TrialBalanceAggregate;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import com.tuowei.erp.finance.ledger.web.TrialBalanceRowResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TrialBalanceService {

    private final TrialBalanceMapper trialBalanceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public TrialBalanceService(TrialBalanceMapper trialBalanceMapper, AuditMetadataFactory auditMetadataFactory) {
        this.trialBalanceMapper = trialBalanceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(TrialBalanceQuery query) {
        validate(query);
        AuditMetadata audit = auditMetadataFactory.current();
        List<TrialBalanceRowResponse> rows = trialBalanceMapper.aggregate(
                        audit.companyId(),
                        audit.accountBookId(),
                        query.getDateFrom(),
                        query.getDateTo()
                ).stream()
                .map(this::toRow)
                .toList();

        BigDecimal openingDebit = sum(rows, TrialBalanceRowResponse::openingDebit);
        BigDecimal openingCredit = sum(rows, TrialBalanceRowResponse::openingCredit);
        BigDecimal periodDebit = sum(rows, TrialBalanceRowResponse::periodDebit);
        BigDecimal periodCredit = sum(rows, TrialBalanceRowResponse::periodCredit);
        BigDecimal closingDebit = sum(rows, TrialBalanceRowResponse::closingDebit);
        BigDecimal closingCredit = sum(rows, TrialBalanceRowResponse::closingCredit);

        return new TrialBalanceResponse(
                query.getDateFrom(),
                query.getDateTo(),
                rows,
                openingDebit,
                openingCredit,
                periodDebit,
                periodCredit,
                closingDebit,
                closingCredit,
                openingDebit.compareTo(openingCredit) == 0,
                periodDebit.compareTo(periodCredit) == 0,
                closingDebit.compareTo(closingCredit) == 0
        );
    }

    private TrialBalanceRowResponse toRow(TrialBalanceAggregate aggregate) {
        BigDecimal openingNet = amount(aggregate.openingDebit()).subtract(amount(aggregate.openingCredit()));
        BigDecimal periodDebit = amount(aggregate.periodDebit());
        BigDecimal periodCredit = amount(aggregate.periodCredit());
        BigDecimal closingNet = openingNet.add(periodDebit).subtract(periodCredit);
        return new TrialBalanceRowResponse(
                aggregate.subjectCode(),
                aggregate.subjectName(),
                aggregate.subjectType(),
                aggregate.balanceDirection(),
                debitSide(openingNet),
                creditSide(openingNet),
                periodDebit,
                periodCredit,
                debitSide(closingNet),
                creditSide(closingNet)
        );
    }

    private BigDecimal debitSide(BigDecimal net) {
        return net.signum() >= 0 ? amount(net) : zero();
    }

    private BigDecimal creditSide(BigDecimal net) {
        return net.signum() < 0 ? amount(net.negate()) : zero();
    }

    private BigDecimal sum(List<TrialBalanceRowResponse> rows, AmountExtractor extractor) {
        return amount(rows.stream()
                .map(extractor::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal amount(BigDecimal value) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(value));
    }

    private BigDecimal zero() {
        return ScalePrecision.amount(BigDecimal.ZERO);
    }

    private void validate(TrialBalanceQuery query) {
        if (query == null || query.getDateFrom() == null || query.getDateTo() == null) {
            throw new IllegalArgumentException("试算平衡日期范围不能为空");
        }
        if (query.getDateFrom().isAfter(query.getDateTo())) {
            throw new IllegalArgumentException("试算平衡开始日期不能晚于结束日期");
        }
    }

    @FunctionalInterface
    private interface AmountExtractor {
        BigDecimal amount(TrialBalanceRowResponse row);
    }
}
