package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceCounterEntity;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SequenceNumberGenerator {

    private static final int MAX_COUNTER_CREATION_ATTEMPTS = 8;
    private static final int MAX_TRANSACTION_RETRY_ATTEMPTS = 8;

    private final SequenceRuleMapper sequenceRuleMapper;
    private final SequenceCounterMapper sequenceCounterMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final TransactionTemplate transactionTemplate;

    public SequenceNumberGenerator(
            SequenceRuleMapper sequenceRuleMapper,
            SequenceCounterMapper sequenceCounterMapper,
            AuditMetadataFactory auditMetadataFactory,
            PlatformTransactionManager transactionManager
    ) {
        this.sequenceRuleMapper = sequenceRuleMapper;
        this.sequenceCounterMapper = sequenceCounterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public String nextNumber(String bizType, String bizLabel, LocalDate bizDate) {
        for (int attempt = 0; attempt < MAX_TRANSACTION_RETRY_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> nextNumberInTransaction(bizType, bizLabel, bizDate));
            } catch (TransientDataAccessException ex) {
                if (attempt == MAX_TRANSACTION_RETRY_ATTEMPTS - 1) {
                    throw new IllegalStateException(bizLabel + "编号生成冲突，请重试", ex);
                }
            }
        }
        throw new IllegalStateException(bizLabel + "编号生成冲突，请重试");
    }

    private String nextNumberInTransaction(String bizType, String bizLabel, LocalDate bizDate) {
        AuditMetadata audit = auditMetadataFactory.current();
        SequenceRuleEntity rule = requireActiveRule(bizType, bizLabel, audit);
        String periodKey = bizDate.format(DateTimeFormatter.ofPattern(rule.getDatePattern()));

        for (int attempt = 0; attempt < MAX_COUNTER_CREATION_ATTEMPTS; attempt++) {
            ensureCounterExists(rule, periodKey, audit);
            Long nextValue = tryIncrementLockedCounter(rule, periodKey, audit);
            if (nextValue != null) {
                mirrorRuleCurrentValue(rule, nextValue, audit);
                String numberPart = String.format("%0" + rule.getSeqLength() + "d", nextValue);
                return rule.getPrefix() + periodKey + numberPart;
            }
        }
        throw new IllegalStateException(bizLabel + "编号生成冲突，请重试");
    }

    private SequenceRuleEntity requireActiveRule(String bizType, String bizLabel, AuditMetadata audit) {
        SequenceRuleEntity rule = sequenceRuleMapper.selectOne(new LambdaQueryWrapper<SequenceRuleEntity>()
                .eq(SequenceRuleEntity::getCompanyId, audit.companyId())
                .eq(SequenceRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(SequenceRuleEntity::getBizType, bizType));
        if (rule == null) {
            throw new IllegalArgumentException(bizLabel + "编号规则不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(rule.getStatus())) {
            throw new IllegalArgumentException(bizLabel + "编号规则已停用");
        }
        return rule;
    }

    private void ensureCounterExists(SequenceRuleEntity rule, String periodKey, AuditMetadata audit) {
        SequenceCounterEntity counter = sequenceCounterMapper.selectOne(counterQuery(rule, periodKey));
        if (counter == null) {
            tryInsertCounter(rule, periodKey, audit);
        }
    }

    private Long tryIncrementLockedCounter(SequenceRuleEntity rule, String periodKey, AuditMetadata audit) {
        SequenceCounterEntity counter = sequenceCounterMapper.selectForUpdate(
                rule.getCompanyId(),
                rule.getAccountBookId(),
                rule.getBizType(),
                periodKey
        );
        if (counter == null) {
            return null;
        }
        long nextValue = zeroDefault(counter.getCurrentValue()) + 1L;
        return sequenceCounterMapper.incrementCurrentValue(
                rule.getCompanyId(),
                rule.getAccountBookId(),
                rule.getBizType(),
                periodKey,
                audit.userId(),
                audit.now()
        ) == 1
                ? nextValue
                : null;
    }

    private void tryInsertCounter(SequenceRuleEntity rule, String periodKey, AuditMetadata audit) {
        LocalDateTime now = audit.now();

        SequenceCounterEntity counter = new SequenceCounterEntity();
        counter.setCompanyId(rule.getCompanyId());
        counter.setAccountBookId(rule.getAccountBookId());
        counter.setBizType(rule.getBizType());
        counter.setPeriodKey(periodKey);
        counter.setCurrentValue(initialCounterValue(rule));
        counter.setCreatedBy(audit.userId());
        counter.setCreatedTime(now);
        counter.setUpdatedBy(audit.userId());
        counter.setUpdatedTime(now);
        counter.setVersion(0);

        try {
            sequenceCounterMapper.insert(counter);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private long initialCounterValue(SequenceRuleEntity rule) {
        Long existingCounterCount = sequenceCounterMapper.selectCount(new LambdaQueryWrapper<SequenceCounterEntity>()
                .eq(SequenceCounterEntity::getCompanyId, rule.getCompanyId())
                .eq(SequenceCounterEntity::getAccountBookId, rule.getAccountBookId())
                .eq(SequenceCounterEntity::getBizType, rule.getBizType()));
        return existingCounterCount == 0L ? zeroDefault(rule.getCurrentValue()) : 0L;
    }

    private LambdaQueryWrapper<SequenceCounterEntity> counterQuery(SequenceRuleEntity rule, String periodKey) {
        return new LambdaQueryWrapper<SequenceCounterEntity>()
                .eq(SequenceCounterEntity::getCompanyId, rule.getCompanyId())
                .eq(SequenceCounterEntity::getAccountBookId, rule.getAccountBookId())
                .eq(SequenceCounterEntity::getBizType, rule.getBizType())
                .eq(SequenceCounterEntity::getPeriodKey, periodKey);
    }

    private void mirrorRuleCurrentValue(SequenceRuleEntity rule, long nextValue, AuditMetadata audit) {
        rule.setCurrentValue(nextValue);
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
        sequenceRuleMapper.updateById(rule);
    }

    private long zeroDefault(Long value) {
        return value == null ? 0L : value;
    }
}
