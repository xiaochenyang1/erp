package com.tuowei.erp.issue.sla.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.sla.mapper.ExceptionSlaPolicyMapper;
import com.tuowei.erp.issue.sla.model.ExceptionSlaPolicyEntity;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/** Read-side SLA policy resolution, tenant-scoped listing and response mapping shared with the command side. */
@Service
public class ExceptionSlaPolicyQueryService {

    private static final String DEFAULT_CATEGORY = "GENERAL";
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    private static final int MIN_DUE_HOURS = 1;
    private static final int MAX_DUE_HOURS = 8760;
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final ExceptionSlaPolicyMapper policyMapper;

    public ExceptionSlaPolicyQueryService(ExceptionSlaPolicyMapper policyMapper) {
        this.policyMapper = policyMapper;
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

    /** Tenant-scoped paged listing shared with the command side (runs inside the caller's transaction). */
    PageResponse<ExceptionSlaPolicyResponse> page(AuditMetadata audit, ExceptionSlaPolicyPageQuery safeQuery) {
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

    ExceptionSlaPolicyEntity requirePolicy(Long id, AuditMetadata audit) {
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
    ExceptionSlaPolicyResponse toResponse(ExceptionSlaPolicyEntity policy) {
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

    void validatePriority(String priority) {
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("优先级不支持");
        }
    }

    String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
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

    private String normalizeCodeOrDefault(String value, String defaultValue) {
        String normalized = normalizeCode(value);
        return normalized == null ? defaultValue : normalized;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
