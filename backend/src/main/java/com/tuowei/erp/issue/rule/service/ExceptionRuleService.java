package com.tuowei.erp.issue.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.model.ExceptionRuleHitEntity;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitPageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExceptionRuleService {

    private static final String RULE_LOW_STOCK = "LOW_STOCK";
    private static final String RULE_RECEIVABLE_OVERDUE = "RECEIVABLE_OVERDUE";
    private static final String RULE_PAYABLE_OVERDUE = "PAYABLE_OVERDUE";
    private static final String RULE_OPERATION_FAILURE = "OPERATION_FAILURE";
    private static final int DEFAULT_SCHEDULE_INTERVAL_MINUTES = 60;
    private static final int MIN_SCHEDULE_INTERVAL_MINUTES = 5;
    private static final int MAX_SCHEDULE_INTERVAL_MINUTES = 10080;
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> THRESHOLD_UNITS = Set.of("QTY", "DAYS", "MINUTES", "COUNT");

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionRuleMapper ruleMapper;
    private final ExceptionRuleHitMapper hitMapper;
    private final ExceptionRuleScanService scanService;

    public ExceptionRuleService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleHitMapper hitMapper,
            ExceptionRuleScanService scanService
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.ruleMapper = ruleMapper;
        this.hitMapper = hitMapper;
        this.scanService = scanService;
    }

    @Transactional
    public PageResponse<ExceptionRuleResponse> list(ExceptionRulePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureBuiltInRules(audit);
        ExceptionRulePageQuery safeQuery = query == null ? new ExceptionRulePageQuery() : query;
        Page<ExceptionRuleEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionRuleEntity> result = ruleMapper.selectPage(page, buildRuleQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRuleResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionRuleHitResponse> listHits(ExceptionRuleHitPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleHitPageQuery safeQuery = query == null ? new ExceptionRuleHitPageQuery() : query;
        Page<ExceptionRuleHitEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionRuleHitEntity> result = hitMapper.selectPage(page, buildHitQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toHitResponse).toList()
        );
    }

    @Transactional
    public ExceptionRuleResponse update(Long id, ExceptionRuleUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        ExceptionRuleUpdateRequest safeRequest = request == null ? new ExceptionRuleUpdateRequest() : request;
        if (safeRequest.getThresholdValue() != null) {
            if (safeRequest.getThresholdValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("阈值不能小于 0");
            }
            rule.setThresholdValue(safeRequest.getThresholdValue());
        }
        String thresholdUnit = normalizeCode(safeRequest.getThresholdUnit());
        if (thresholdUnit != null) {
            if (!THRESHOLD_UNITS.contains(thresholdUnit)) {
                throw new IllegalArgumentException("阈值单位不支持");
            }
            rule.setThresholdUnit(thresholdUnit);
        }
        String priority = normalizeCode(safeRequest.getPriority());
        if (priority != null) {
            if (!PRIORITIES.contains(priority)) {
                throw new IllegalArgumentException("优先级不支持");
            }
            rule.setPriority(priority);
        }
        if (safeRequest.getScheduleIntervalMinutes() != null) {
            int interval = safeRequest.getScheduleIntervalMinutes();
            if (interval < MIN_SCHEDULE_INTERVAL_MINUTES || interval > MAX_SCHEDULE_INTERVAL_MINUTES) {
                throw new IllegalArgumentException("扫描间隔必须在 5 到 10080 分钟之间");
            }
            rule.setScheduleIntervalMinutes(interval);
        }
        rule.setAssigneeUserId(safeRequest.getAssigneeUserId());
        rule.setRemark(truncate(trimToNull(safeRequest.getRemark()), 512));
        touch(rule, audit);
        ruleMapper.updateById(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public ExceptionRuleResponse enable(Long id) {
        return updateEnabled(id, 1);
    }

    @Transactional
    public ExceptionRuleResponse disable(Long id) {
        return updateEnabled(id, 0);
    }

    @Transactional
    public ExceptionRuleScanResultResponse scanRule(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        if (!Integer.valueOf(1).equals(rule.getEnabled())) {
            throw new IllegalArgumentException("异常规则已停用，不能执行扫描");
        }
        return scanService.scanRule(rule, audit);
    }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanAll() {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureBuiltInRules(audit);
        List<ExceptionRuleEntity> rules = ruleMapper.selectList(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0)
                .eq(ExceptionRuleEntity::getEnabled, 1)
                .orderByAsc(ExceptionRuleEntity::getId));
        return scanService.scanRules(rules, audit);
    }

    @Transactional
    public List<ExceptionRuleScanResultResponse> scanDueRules() {
        return scanService.scanDueRules();
    }

    private void ensureBuiltInRules(AuditMetadata audit) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            return;
        }
        builtInRule(audit, "LOW_STOCK_DEFAULT", "低库存自动工单", RULE_LOW_STOCK,
                "LOW_STOCK", "HIGH", BigDecimal.ZERO, "QTY", "扫描库存预警规则命中的低库存项目");
        builtInRule(audit, "RECEIVABLE_OVERDUE_DEFAULT", "应收逾期自动工单", RULE_RECEIVABLE_OVERDUE,
                "PAYMENT_OVERDUE", "HIGH", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应收单");
        builtInRule(audit, "PAYABLE_OVERDUE_DEFAULT", "应付逾期自动工单", RULE_PAYABLE_OVERDUE,
                "PAYMENT_OVERDUE", "MEDIUM", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应付单");
        builtInRule(audit, "OPERATION_FAILURE_DEFAULT", "失败操作日志自动工单", RULE_OPERATION_FAILURE,
                "SYSTEM_ERROR", "MEDIUM", new BigDecimal("1440"), "MINUTES", "扫描最近窗口内的失败操作日志");
    }

    private void builtInRule(
            AuditMetadata audit,
            String ruleCode,
            String ruleName,
            String ruleType,
            String category,
            String priority,
            BigDecimal thresholdValue,
            String thresholdUnit,
            String remark
    ) {
        ExceptionRuleEntity rule = new ExceptionRuleEntity();
        rule.setCompanyId(audit.companyId());
        rule.setAccountBookId(audit.accountBookId());
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType);
        rule.setCategory(category);
        rule.setPriority(priority);
        rule.setThresholdValue(thresholdValue);
        rule.setThresholdUnit(thresholdUnit);
        rule.setEnabled(1);
        rule.setRemark(remark);
        rule.setScheduleIntervalMinutes(DEFAULT_SCHEDULE_INTERVAL_MINUTES);
        rule.setLastHitCount(0);
        rule.setLastTicketCreatedCount(0);
        rule.setDeletedFlag(0);
        rule.setCreatedBy(audit.userId());
        rule.setCreatedTime(audit.now());
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
        rule.setVersion(0);
        ruleMapper.insert(rule);
    }

    private ExceptionRuleResponse updateEnabled(Long id, Integer enabled) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        rule.setEnabled(enabled);
        touch(rule, audit);
        ruleMapper.updateById(rule);
        return toRuleResponse(rule);
    }

    private LambdaQueryWrapper<ExceptionRuleEntity> buildRuleQuery(AuditMetadata audit, ExceptionRulePageQuery query) {
        LambdaQueryWrapper<ExceptionRuleEntity> wrapper = new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested
                    .like(ExceptionRuleEntity::getRuleCode, keyword)
                    .or()
                    .like(ExceptionRuleEntity::getRuleName, keyword)
                    .or()
                    .like(ExceptionRuleEntity::getRemark, keyword));
        }
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) {
            wrapper.eq(ExceptionRuleEntity::getRuleType, ruleType);
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ExceptionRuleEntity::getEnabled, Boolean.TRUE.equals(query.getEnabled()) ? 1 : 0);
        }
        return wrapper.orderByDesc(ExceptionRuleEntity::getUpdatedTime).orderByDesc(ExceptionRuleEntity::getId);
    }

    private LambdaQueryWrapper<ExceptionRuleHitEntity> buildHitQuery(AuditMetadata audit, ExceptionRuleHitPageQuery query) {
        LambdaQueryWrapper<ExceptionRuleHitEntity> wrapper = new LambdaQueryWrapper<ExceptionRuleHitEntity>()
                .eq(ExceptionRuleHitEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleHitEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleHitEntity::getDeletedFlag, 0);
        if (query.getRuleId() != null) {
            wrapper.eq(ExceptionRuleHitEntity::getRuleId, query.getRuleId());
        }
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) {
            wrapper.eq(ExceptionRuleHitEntity::getRuleType, ruleType);
        }
        String sourceNo = trimToNull(query.getSourceNo());
        if (sourceNo != null) {
            wrapper.like(ExceptionRuleHitEntity::getSourceNo, sourceNo);
        }
        if (query.getTicketId() != null) {
            wrapper.eq(ExceptionRuleHitEntity::getTicketId, query.getTicketId());
        }
        return wrapper.orderByDesc(ExceptionRuleHitEntity::getLastHitTime).orderByDesc(ExceptionRuleHitEntity::getId);
    }

    private ExceptionRuleEntity requireRule(Long id, AuditMetadata audit) {
        ExceptionRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0)
                .eq(ExceptionRuleEntity::getId, id));
        if (rule == null) {
            throw new IllegalArgumentException("异常规则不存在");
        }
        return rule;
    }

    private int scheduleIntervalMinutes(ExceptionRuleEntity rule) {
        Integer interval = rule.getScheduleIntervalMinutes();
        if (interval == null) {
            return DEFAULT_SCHEDULE_INTERVAL_MINUTES;
        }
        if (interval < MIN_SCHEDULE_INTERVAL_MINUTES) {
            return MIN_SCHEDULE_INTERVAL_MINUTES;
        }
        if (interval > MAX_SCHEDULE_INTERVAL_MINUTES) {
            return MAX_SCHEDULE_INTERVAL_MINUTES;
        }
        return interval;
    }

    private void touch(ExceptionRuleEntity rule, AuditMetadata audit) {
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
    }

    private ExceptionRuleResponse toRuleResponse(ExceptionRuleEntity rule) {
        return new ExceptionRuleResponse(
                rule.getId(),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getRuleType(),
                rule.getCategory(),
                rule.getPriority(),
                rule.getThresholdValue(),
                rule.getThresholdUnit(),
                Integer.valueOf(1).equals(rule.getEnabled()),
                rule.getAssigneeUserId(),
                scheduleIntervalMinutes(rule),
                rule.getNextScanTime(),
                rule.getRemark(),
                rule.getLastScanTime(),
                rule.getLastScanStatus(),
                rule.getLastHitCount(),
                rule.getLastTicketCreatedCount(),
                rule.getLastErrorMessage(),
                rule.getUpdatedTime()
        );
    }

    private ExceptionRuleHitResponse toHitResponse(ExceptionRuleHitEntity hit) {
        return new ExceptionRuleHitResponse(
                hit.getId(),
                hit.getRuleId(),
                hit.getRuleCode(),
                hit.getRuleType(),
                hit.getSourceType(),
                hit.getSourceId(),
                hit.getSourceNo(),
                hit.getSourceRoute(),
                hit.getHitKey(),
                hit.getTitle(),
                hit.getDescription(),
                hit.getTriggerValue(),
                hit.getThresholdValue(),
                hit.getTicketId(),
                hit.getHitCount(),
                hit.getFirstHitTime(),
                hit.getLastHitTime()
        );
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
