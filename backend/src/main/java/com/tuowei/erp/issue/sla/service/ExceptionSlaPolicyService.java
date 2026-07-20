package com.tuowei.erp.issue.sla.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.sla.mapper.ExceptionSlaPolicyMapper;
import com.tuowei.erp.issue.sla.model.ExceptionSlaPolicyEntity;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class ExceptionSlaPolicyService {

    private static final String DEFAULT_CATEGORY = "GENERAL";
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    private static final int MIN_DUE_HOURS = 1;
    private static final int MAX_DUE_HOURS = 8760;
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionSlaPolicyMapper policyMapper;

    public ExceptionSlaPolicyService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionSlaPolicyMapper policyMapper
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.policyMapper = policyMapper;
    }

    @Transactional
    public PageResponse<ExceptionSlaPolicyResponse> list(ExceptionSlaPolicyPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureDefaultPolicies(audit);
        ExceptionSlaPolicyPageQuery safeQuery = query == null ? new ExceptionSlaPolicyPageQuery() : query;
        Page<ExceptionSlaPolicyEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionSlaPolicyEntity> result = policyMapper.selectPage(page, buildQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional
    public ExceptionSlaPolicyResponse update(Long id, ExceptionSlaPolicyUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionSlaPolicyEntity policy = requirePolicy(id, audit);
        ExceptionSlaPolicyUpdateRequest safeRequest = request == null ? new ExceptionSlaPolicyUpdateRequest() : request;
        if (safeRequest.getDueHours() != null) {
            int dueHours = safeRequest.getDueHours();
            if (dueHours < MIN_DUE_HOURS || dueHours > MAX_DUE_HOURS) {
                throw new IllegalArgumentException("SLA时限必须在 1 到 8760 小时之间");
            }
            policy.setDueHours(dueHours);
        }
        if (safeRequest.getEscalationEnabled() != null) {
            policy.setEscalationEnabled(Boolean.TRUE.equals(safeRequest.getEscalationEnabled()) ? 1 : 0);
        }
        String escalateToPriority = normalizeCode(safeRequest.getEscalateToPriority());
        if (escalateToPriority != null) {
            validatePriority(escalateToPriority);
            policy.setEscalateToPriority(escalateToPriority);
        }
        if (safeRequest.getEnabled() != null) {
            policy.setEnabled(Boolean.TRUE.equals(safeRequest.getEnabled()) ? 1 : 0);
        }
        policy.setRemark(truncate(trimToNull(safeRequest.getRemark()), 512));
        touch(policy, audit);
        policyMapper.updateById(policy);
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public LocalDateTime resolveDueTime(String category, String priority, LocalDateTime createdAt, AuditMetadata audit) {
        AuditMetadata safeAudit = requireAudit(audit);
        String normalizedCategory = normalizeCodeOrDefault(category, DEFAULT_CATEGORY);
        String normalizedPriority = normalizeCodeOrDefault(priority, DEFAULT_PRIORITY);
        validatePriority(normalizedPriority);
        LocalDateTime baseTime = createdAt == null ? safeAudit.now() : createdAt;
        ExceptionSlaPolicyEntity policy = findEnabledPolicy(safeAudit, normalizedCategory, normalizedPriority);
        if (policy == null && !DEFAULT_CATEGORY.equals(normalizedCategory)) {
            policy = findEnabledPolicy(safeAudit, DEFAULT_CATEGORY, normalizedPriority);
        }
        int dueHours = policy == null ? defaultDueHours(normalizedPriority) : safeDueHours(policy.getDueHours(), normalizedPriority);
        return baseTime.plusHours(dueHours);
    }

    @Transactional(readOnly = true)
    public ExceptionSlaEscalationPolicy resolveEscalation(ExceptionTicketEntity ticket, AuditMetadata audit) {
        AuditMetadata safeAudit = requireAudit(audit);
        String normalizedCategory = normalizeCodeOrDefault(ticket == null ? null : ticket.getCategory(), DEFAULT_CATEGORY);
        String normalizedPriority = normalizeCodeOrDefault(ticket == null ? null : ticket.getPriority(), DEFAULT_PRIORITY);
        validatePriority(normalizedPriority);
        ExceptionSlaPolicyEntity policy = findEnabledPolicy(safeAudit, normalizedCategory, normalizedPriority);
        if (policy == null && !DEFAULT_CATEGORY.equals(normalizedCategory)) {
            policy = findEnabledPolicy(safeAudit, DEFAULT_CATEGORY, normalizedPriority);
        }
        if (policy == null) {
            return new ExceptionSlaEscalationPolicy(true, nextPriority(normalizedPriority));
        }
        if (!Integer.valueOf(1).equals(policy.getEscalationEnabled())) {
            return new ExceptionSlaEscalationPolicy(false, normalizedPriority);
        }
        String targetPriority = normalizeCodeOrDefault(policy.getEscalateToPriority(), nextPriority(normalizedPriority));
        validatePriority(targetPriority);
        return new ExceptionSlaEscalationPolicy(true, targetPriority);
    }

    private void ensureDefaultPolicies(AuditMetadata audit) {
        Long count = policyMapper.selectCount(new LambdaQueryWrapper<ExceptionSlaPolicyEntity>()
                .eq(ExceptionSlaPolicyEntity::getCompanyId, audit.companyId())
                .eq(ExceptionSlaPolicyEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionSlaPolicyEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            return;
        }
        defaultPolicy(audit, DEFAULT_CATEGORY, "LOW", 168, true, "MEDIUM", "通用低优先级 SLA");
        defaultPolicy(audit, DEFAULT_CATEGORY, "MEDIUM", 72, true, "HIGH", "通用中优先级 SLA");
        defaultPolicy(audit, DEFAULT_CATEGORY, "HIGH", 24, true, "URGENT", "通用高优先级 SLA");
        defaultPolicy(audit, DEFAULT_CATEGORY, "URGENT", 4, true, "URGENT", "通用紧急 SLA");
        defaultPolicy(audit, "LOW_STOCK", "HIGH", 24, true, "URGENT", "低库存高优先级 SLA");
        defaultPolicy(audit, "PAYMENT_OVERDUE", "MEDIUM", 72, true, "HIGH", "逾期收付中优先级 SLA");
        defaultPolicy(audit, "PAYMENT_OVERDUE", "HIGH", 24, true, "URGENT", "逾期收付高优先级 SLA");
        defaultPolicy(audit, "SYSTEM_ERROR", "MEDIUM", 72, true, "HIGH", "系统失败中优先级 SLA");
    }

    private void defaultPolicy(
            AuditMetadata audit,
            String category,
            String priority,
            int dueHours,
            boolean escalationEnabled,
            String escalateToPriority,
            String remark
    ) {
        ExceptionSlaPolicyEntity policy = new ExceptionSlaPolicyEntity();
        policy.setCompanyId(audit.companyId());
        policy.setAccountBookId(audit.accountBookId());
        policy.setCategory(category);
        policy.setPriority(priority);
        policy.setDueHours(dueHours);
        policy.setEscalationEnabled(escalationEnabled ? 1 : 0);
        policy.setEscalateToPriority(escalateToPriority);
        policy.setEnabled(1);
        policy.setRemark(remark);
        policy.setDeletedFlag(0);
        policy.setCreatedBy(audit.userId());
        policy.setCreatedTime(audit.now());
        policy.setUpdatedBy(audit.userId());
        policy.setUpdatedTime(audit.now());
        policy.setVersion(0);
        policyMapper.insert(policy);
    }

    private ExceptionSlaPolicyEntity findEnabledPolicy(AuditMetadata audit, String category, String priority) {
        return policyMapper.selectOne(new LambdaQueryWrapper<ExceptionSlaPolicyEntity>()
                .eq(ExceptionSlaPolicyEntity::getCompanyId, audit.companyId())
                .eq(ExceptionSlaPolicyEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionSlaPolicyEntity::getDeletedFlag, 0)
                .eq(ExceptionSlaPolicyEntity::getEnabled, 1)
                .eq(ExceptionSlaPolicyEntity::getCategory, category)
                .eq(ExceptionSlaPolicyEntity::getPriority, priority));
    }

    private LambdaQueryWrapper<ExceptionSlaPolicyEntity> buildQuery(AuditMetadata audit, ExceptionSlaPolicyPageQuery query) {
        LambdaQueryWrapper<ExceptionSlaPolicyEntity> wrapper = new LambdaQueryWrapper<ExceptionSlaPolicyEntity>()
                .eq(ExceptionSlaPolicyEntity::getCompanyId, audit.companyId())
                .eq(ExceptionSlaPolicyEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionSlaPolicyEntity::getDeletedFlag, 0);
        String category = normalizeCode(query.getCategory());
        if (category != null) {
            wrapper.eq(ExceptionSlaPolicyEntity::getCategory, category);
        }
        String priority = normalizeCode(query.getPriority());
        if (priority != null) {
            validatePriority(priority);
            wrapper.eq(ExceptionSlaPolicyEntity::getPriority, priority);
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ExceptionSlaPolicyEntity::getEnabled, Boolean.TRUE.equals(query.getEnabled()) ? 1 : 0);
        }
        return wrapper.orderByAsc(ExceptionSlaPolicyEntity::getCategory)
                .orderByAsc(ExceptionSlaPolicyEntity::getPriority)
                .orderByDesc(ExceptionSlaPolicyEntity::getUpdatedTime);
    }

    private ExceptionSlaPolicyEntity requirePolicy(Long id, AuditMetadata audit) {
        ExceptionSlaPolicyEntity policy = policyMapper.selectOne(new LambdaQueryWrapper<ExceptionSlaPolicyEntity>()
                .eq(ExceptionSlaPolicyEntity::getCompanyId, audit.companyId())
                .eq(ExceptionSlaPolicyEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionSlaPolicyEntity::getDeletedFlag, 0)
                .eq(ExceptionSlaPolicyEntity::getId, id));
        if (policy == null) {
            throw new IllegalArgumentException("异常SLA策略不存在");
        }
        return policy;
    }

    private ExceptionSlaPolicyResponse toResponse(ExceptionSlaPolicyEntity policy) {
        return new ExceptionSlaPolicyResponse(
                policy.getId(),
                policy.getCategory(),
                policy.getPriority(),
                policy.getDueHours(),
                Integer.valueOf(1).equals(policy.getEscalationEnabled()),
                policy.getEscalateToPriority(),
                Integer.valueOf(1).equals(policy.getEnabled()),
                policy.getRemark(),
                policy.getUpdatedTime()
        );
    }

    private AuditMetadata requireAudit(AuditMetadata audit) {
        if (audit == null) {
            throw new IllegalArgumentException("SLA策略解析缺少租户上下文");
        }
        return audit;
    }

    private int safeDueHours(Integer dueHours, String priority) {
        if (dueHours == null || dueHours < MIN_DUE_HOURS || dueHours > MAX_DUE_HOURS) {
            return defaultDueHours(priority);
        }
        return dueHours;
    }

    private int defaultDueHours(String priority) {
        return switch (priority) {
            case "URGENT" -> 4;
            case "HIGH" -> 24;
            case "LOW" -> 168;
            default -> 72;
        };
    }

    private String nextPriority(String priority) {
        return switch (priority) {
            case "LOW" -> "MEDIUM";
            case "MEDIUM" -> "HIGH";
            case "HIGH" -> "URGENT";
            default -> "URGENT";
        };
    }

    private void validatePriority(String priority) {
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("优先级不支持");
        }
    }

    private void touch(ExceptionSlaPolicyEntity policy, AuditMetadata audit) {
        policy.setUpdatedBy(audit.userId());
        policy.setUpdatedTime(audit.now());
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeCodeOrDefault(String value, String defaultValue) {
        String normalized = normalizeCode(value);
        return normalized == null ? defaultValue : normalized;
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
