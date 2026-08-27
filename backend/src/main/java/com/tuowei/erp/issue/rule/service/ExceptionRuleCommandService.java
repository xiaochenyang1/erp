package com.tuowei.erp.issue.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
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

/** Built-in rule bootstrap, configuration commands and scan orchestration. */
@Service
public class ExceptionRuleCommandService {

    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> THRESHOLD_UNITS = Set.of("QTY", "DAYS", "MINUTES", "COUNT");
    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionRuleMapper ruleMapper;
    private final ExceptionRuleScanService scanService;
    private final ExceptionRuleQueryService queryService;

    public ExceptionRuleCommandService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleScanService scanService,
            ExceptionRuleQueryService queryService
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.ruleMapper = ruleMapper;
        this.scanService = scanService;
        this.queryService = queryService;
    }

    @Transactional
    public void ensureBuiltInRules() {
        ensureBuiltInRules(auditMetadataFactory.current());
    }

    @Transactional
    public ExceptionRuleResponse update(Long id, ExceptionRuleUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        ExceptionRuleUpdateRequest safeRequest = request == null ? new ExceptionRuleUpdateRequest() : request;
        if (safeRequest.getThresholdValue() != null) {
            if (safeRequest.getThresholdValue().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("阈值不能小于 0");
            rule.setThresholdValue(safeRequest.getThresholdValue());
        }
        String thresholdUnit = normalizeCode(safeRequest.getThresholdUnit());
        if (thresholdUnit != null) {
            if (!THRESHOLD_UNITS.contains(thresholdUnit)) throw new IllegalArgumentException("阈值单位不支持");
            rule.setThresholdUnit(thresholdUnit);
        }
        String priority = normalizeCode(safeRequest.getPriority());
        if (priority != null) {
            if (!PRIORITIES.contains(priority)) throw new IllegalArgumentException("优先级不支持");
            rule.setPriority(priority);
        }
        if (safeRequest.getScheduleIntervalMinutes() != null) {
            int interval = safeRequest.getScheduleIntervalMinutes();
            if (interval < ExceptionRuleQueryService.MIN_SCHEDULE_INTERVAL_MINUTES
                    || interval > ExceptionRuleQueryService.MAX_SCHEDULE_INTERVAL_MINUTES) {
                throw new IllegalArgumentException("扫描间隔必须在 5 到 10080 分钟之间");
            }
            rule.setScheduleIntervalMinutes(interval);
        }
        rule.setAssigneeUserId(safeRequest.getAssigneeUserId());
        rule.setRemark(truncate(trimToNull(safeRequest.getRemark()), 512));
        touch(rule, audit);
        ruleMapper.updateById(rule);
        return queryService.toRuleResponse(rule);
    }

    @Transactional
    public ExceptionRuleResponse enable(Long id) { return updateEnabled(id, 1); }

    @Transactional
    public ExceptionRuleResponse disable(Long id) { return updateEnabled(id, 0); }

    @Transactional
    public ExceptionRuleScanResultResponse scanRule(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        if (!Integer.valueOf(1).equals(rule.getEnabled())) throw new IllegalArgumentException("异常规则已停用，不能执行扫描");
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
    public List<ExceptionRuleScanResultResponse> scanDueRules() { return scanService.scanDueRules(); }

    private void ensureBuiltInRules(AuditMetadata audit) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0));
        if (count != null && count > 0) return;
        builtInRule(audit, "LOW_STOCK_DEFAULT", "低库存自动工单", "LOW_STOCK", "LOW_STOCK", "HIGH", BigDecimal.ZERO, "QTY", "扫描库存预警规则命中的低库存项目");
        builtInRule(audit, "RECEIVABLE_OVERDUE_DEFAULT", "应收逾期自动工单", "RECEIVABLE_OVERDUE", "PAYMENT_OVERDUE", "HIGH", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应收单");
        builtInRule(audit, "PAYABLE_OVERDUE_DEFAULT", "应付逾期自动工单", "PAYABLE_OVERDUE", "PAYMENT_OVERDUE", "MEDIUM", new BigDecimal("30"), "DAYS", "扫描超过阈值天数仍未结清的应付单");
        builtInRule(audit, "OPERATION_FAILURE_DEFAULT", "失败操作日志自动工单", "OPERATION_FAILURE", "SYSTEM_ERROR", "MEDIUM", new BigDecimal("1440"), "MINUTES", "扫描最近窗口内的失败操作日志");
    }

    private void builtInRule(AuditMetadata audit, String code, String name, String type, String category,
                             String priority, BigDecimal threshold, String unit, String remark) {
        ExceptionRuleEntity rule = new ExceptionRuleEntity();
        rule.setCompanyId(audit.companyId()); rule.setAccountBookId(audit.accountBookId());
        rule.setRuleCode(code); rule.setRuleName(name); rule.setRuleType(type); rule.setCategory(category);
        rule.setPriority(priority); rule.setThresholdValue(threshold); rule.setThresholdUnit(unit); rule.setEnabled(1);
        rule.setRemark(remark); rule.setScheduleIntervalMinutes(ExceptionRuleQueryService.DEFAULT_SCHEDULE_INTERVAL_MINUTES);
        rule.setLastHitCount(0); rule.setLastTicketCreatedCount(0); rule.setDeletedFlag(0);
        rule.setCreatedBy(audit.userId()); rule.setCreatedTime(audit.now()); rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now()); rule.setVersion(0); ruleMapper.insert(rule);
    }

    private ExceptionRuleResponse updateEnabled(Long id, Integer enabled) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleEntity rule = requireRule(id, audit);
        rule.setEnabled(enabled); touch(rule, audit); ruleMapper.updateById(rule);
        return queryService.toRuleResponse(rule);
    }

    private ExceptionRuleEntity requireRule(Long id, AuditMetadata audit) {
        ExceptionRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0)
                .eq(ExceptionRuleEntity::getId, id));
        if (rule == null) throw new IllegalArgumentException("异常规则不存在");
        return rule;
    }

    private void touch(ExceptionRuleEntity rule, AuditMetadata audit) {
        rule.setUpdatedBy(audit.userId()); rule.setUpdatedTime(audit.now());
    }

    private String normalizeCode(String value) { return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null; }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String truncate(String value, int maxLength) { return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength); }
}
