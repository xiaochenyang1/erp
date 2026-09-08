package com.tuowei.erp.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceCounterEntity;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Database-backed exception-ticket number allocation.
 *
 * <p>The shared sequence generator locks a tenant/period counter in the
 * database, so ticket numbers remain unique when more than one application
 * instance creates tickets at the same time.</p>
 */
@Component
public class ExceptionTicketNumberService {

    private static final String BIZ_TYPE = "EXCEPTION_TICKET";
    private static final String BIZ_LABEL = "异常工单";
    private static final String DEFAULT_PREFIX = "ET-";
    private static final String DEFAULT_DATE_PATTERN = "yyyyMMdd-";
    private static final int DEFAULT_SEQUENCE_LENGTH = 4;
    /** Sequence counters are stored as BIGINT and the ticket format is width bounded. */
    private static final int MAX_SEQUENCE_LENGTH = 18;
    private static final int MAX_RECONCILIATION_ATTEMPTS = 8;

    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final SequenceRuleMapper sequenceRuleMapper;
    private final SequenceCounterMapper sequenceCounterMapper;
    private final ExceptionTicketMapper exceptionTicketMapper;

    /**
     * The explicit constructor keeps the service easy to use from small unit
     * tests and from older integrations that only supplied the shared
     * generator.  Spring uses the full constructor below.
     */
    public ExceptionTicketNumberService(SequenceNumberGenerator sequenceNumberGenerator) {
        this(sequenceNumberGenerator, null, null, null);
    }

    @Autowired
    public ExceptionTicketNumberService(
            SequenceNumberGenerator sequenceNumberGenerator,
            SequenceRuleMapper sequenceRuleMapper,
            SequenceCounterMapper sequenceCounterMapper,
            ExceptionTicketMapper exceptionTicketMapper
    ) {
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.sequenceRuleMapper = sequenceRuleMapper;
        this.sequenceCounterMapper = sequenceCounterMapper;
        this.exceptionTicketMapper = exceptionTicketMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = DuplicateKeyException.class)
    public String nextTicketNo(AuditMetadata audit) {
        validateAudit(audit);
        if (!hasReconciliationCollaborators()) {
            return sequenceNumberGenerator.nextNumber(BIZ_TYPE, BIZ_LABEL,
                    audit.now().toLocalDate(), audit);
        }

        SequenceRuleEntity rule = ensureRule(audit);
        validateRule(rule);
        LocalDate bizDate = audit.now().toLocalDate();
        String periodKey = periodKey(rule, bizDate);
        long historicalMaximum = historicalMaximum(audit, rule, periodKey);
        long maximumRepresentable = maximumRepresentable(rule.getSeqLength());
        if (historicalMaximum >= maximumRepresentable) {
            throw new IllegalStateException("异常工单编号流水已达到当前规则上限，请调整日期或编号规则");
        }
        reconcileCounter(rule, periodKey, historicalMaximum, audit);
        String number = sequenceNumberGenerator.nextNumber(BIZ_TYPE, BIZ_LABEL, bizDate, audit);
        // A concurrent allocator can advance the counter after the historical
        // reconciliation read.  Reject a malformed width rather than storing
        // a five-digit value under the documented ET-yyyyMMdd-#### format.
        String expectedPrefix = rule.getPrefix() + periodKey;
        String suffix = number != null && number.startsWith(expectedPrefix)
                ? number.substring(expectedPrefix.length())
                : null;
        if (suffix == null || suffix.length() != rule.getSeqLength() || !allDigits(suffix)) {
            throw new IllegalStateException("异常工单编号超出当前规则位数，请调整日期或编号规则");
        }
        return number;
    }

    private void validateAudit(AuditMetadata audit) {
        if (audit == null || audit.now() == null
                || audit.companyId() == null || audit.accountBookId() == null) {
            throw new IllegalArgumentException("异常工单编号缺少账套上下文");
        }
    }

    private boolean hasReconciliationCollaborators() {
        return sequenceRuleMapper != null
                && sequenceCounterMapper != null
                && exceptionTicketMapper != null;
    }

    private SequenceRuleEntity ensureRule(AuditMetadata audit) {
        SequenceRuleEntity existing = findRule(audit);
        if (existing != null) {
            // A disabled rule is an explicit administrator decision.  Runtime
            // provisioning must never silently re-enable it.
            if (!"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new IllegalArgumentException("异常工单编号规则已停用");
            }
            return existing;
        }

        SequenceRuleEntity created = new SequenceRuleEntity();
        created.setCompanyId(audit.companyId());
        created.setAccountBookId(audit.accountBookId());
        created.setBizType(BIZ_TYPE);
        created.setPrefix(DEFAULT_PREFIX);
        created.setDatePattern(DEFAULT_DATE_PATTERN);
        created.setSeqLength(DEFAULT_SEQUENCE_LENGTH);
        created.setCurrentValue(0L);
        created.setStatus("ACTIVE");
        created.setCreatedBy(audit.userId());
        created.setCreatedTime(audit.now());
        created.setUpdatedBy(audit.userId());
        created.setUpdatedTime(audit.now());
        created.setVersion(0);

        try {
            sequenceRuleMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            // Another application instance may have provisioned the same
            // tenant rule between our read and insert.  Re-read the winner.
            SequenceRuleEntity concurrent = findRule(audit);
            if (concurrent != null) {
                if (!"ACTIVE".equalsIgnoreCase(concurrent.getStatus())) {
                    throw new IllegalArgumentException("异常工单编号规则已停用");
                }
                return concurrent;
            }
            throw new IllegalStateException("异常工单编号规则创建失败，请重试", ex);
        }
    }

    private SequenceRuleEntity findRule(AuditMetadata audit) {
        return sequenceRuleMapper.selectOne(new LambdaQueryWrapper<SequenceRuleEntity>()
                .eq(SequenceRuleEntity::getCompanyId, audit.companyId())
                .eq(SequenceRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(SequenceRuleEntity::getBizType, BIZ_TYPE));
    }

    private void validateRule(SequenceRuleEntity rule) {
        if (!StringUtils.hasText(rule.getPrefix()) || !StringUtils.hasText(rule.getDatePattern())
                || rule.getSeqLength() == null || rule.getSeqLength() < 1
                || rule.getSeqLength() > MAX_SEQUENCE_LENGTH) {
            throw new IllegalStateException("异常工单编号规则定义无效");
        }
        try {
            DateTimeFormatter.ofPattern(rule.getDatePattern());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("异常工单编号规则日期格式无效", ex);
        }
    }

    private String periodKey(SequenceRuleEntity rule, LocalDate bizDate) {
        return bizDate.format(DateTimeFormatter.ofPattern(rule.getDatePattern()));
    }

    private long maximumRepresentable(int sequenceLength) {
        long power = 1L;
        for (int index = 0; index < sequenceLength; index++) {
            power = Math.multiplyExact(power, 10L);
        }
        return power - 1L;
    }

    private long historicalMaximum(AuditMetadata audit, SequenceRuleEntity rule, String periodKey) {
        String numberPrefix = rule.getPrefix() + periodKey;
        List<ExceptionTicketEntity> tickets = exceptionTicketMapper.selectList(
                new LambdaQueryWrapper<ExceptionTicketEntity>()
                        .select(ExceptionTicketEntity::getTicketNo)
                        .eq(ExceptionTicketEntity::getCompanyId, audit.companyId())
                        .eq(ExceptionTicketEntity::getAccountBookId, audit.accountBookId())
                        .likeRight(ExceptionTicketEntity::getTicketNo, numberPrefix)
        );
        if (tickets == null || tickets.isEmpty()) {
            return 0L;
        }
        long maximum = 0L;
        for (ExceptionTicketEntity ticket : tickets) {
            if (ticket == null || ticket.getTicketNo() == null
                    || !ticket.getTicketNo().startsWith(numberPrefix)) {
                continue;
            }
            String suffix = ticket.getTicketNo().substring(numberPrefix.length());
            if (suffix.isEmpty() || !allDigits(suffix)) {
                continue;
            }
            try {
                maximum = Math.max(maximum, Long.parseLong(suffix));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("历史异常工单编号流水超出可支持范围", ex);
            }
        }
        return maximum;
    }

    private boolean allDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }

    private void reconcileCounter(
            SequenceRuleEntity rule,
            String periodKey,
            long historicalMaximum,
            AuditMetadata audit
    ) {
        if (historicalMaximum <= 0L) {
            return;
        }
        for (int attempt = 0; attempt < MAX_RECONCILIATION_ATTEMPTS; attempt++) {
            SequenceCounterEntity counter = sequenceCounterMapper.selectForUpdate(
                    rule.getCompanyId(), rule.getAccountBookId(), rule.getBizType(), periodKey);
            if (counter == null) {
                if (insertCounter(rule, periodKey, historicalMaximum, audit)) {
                    return;
                }
                continue;
            }

            long currentValue = counter.getCurrentValue() == null ? 0L : counter.getCurrentValue();
            if (currentValue >= historicalMaximum) {
                return;
            }
            counter.setCurrentValue(historicalMaximum);
            counter.setUpdatedBy(audit.userId());
            counter.setUpdatedTime(audit.now());
            if (sequenceCounterMapper.updateById(counter) == 1) {
                return;
            }
        }
        throw new IllegalStateException("异常工单编号流水对齐失败，请重试");
    }

    private boolean insertCounter(
            SequenceRuleEntity rule,
            String periodKey,
            long currentValue,
            AuditMetadata audit
    ) {
        SequenceCounterEntity counter = new SequenceCounterEntity();
        counter.setCompanyId(rule.getCompanyId());
        counter.setAccountBookId(rule.getAccountBookId());
        counter.setBizType(rule.getBizType());
        counter.setPeriodKey(periodKey);
        counter.setCurrentValue(currentValue);
        counter.setCreatedBy(audit.userId());
        counter.setCreatedTime(audit.now());
        counter.setUpdatedBy(audit.userId());
        counter.setUpdatedTime(audit.now());
        counter.setVersion(0);
        try {
            sequenceCounterMapper.insert(counter);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
