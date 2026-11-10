package com.tuowei.erp.issue.sla.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.sla.mapper.ExceptionSlaPolicyMapper;
import com.tuowei.erp.issue.sla.model.ExceptionSlaPolicyEntity;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Write-side SLA policy listing (with lazy default seeding) and configuration updates, reusing the query service for reads. */
@Service
public class ExceptionSlaPolicyCommandService {

    private static final String DEFAULT_CATEGORY = "GENERAL";
    private static final int MIN_DUE_HOURS = 1;
    private static final int MAX_DUE_HOURS = 8760;

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionSlaPolicyMapper policyMapper;
    private final ExceptionSlaPolicyQueryService queryService;

    public ExceptionSlaPolicyCommandService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionSlaPolicyMapper policyMapper,
            ExceptionSlaPolicyQueryService queryService
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.policyMapper = policyMapper;
        this.queryService = queryService;
    }

    @Transactional
    public PageResponse<ExceptionSlaPolicyResponse> list(ExceptionSlaPolicyPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ensureDefaultPolicies(audit);
        ExceptionSlaPolicyPageQuery safeQuery = query == null ? new ExceptionSlaPolicyPageQuery() : query;
        return queryService.page(audit, safeQuery);
    }
    @Transactional
    public ExceptionSlaPolicyResponse update(Long id, ExceptionSlaPolicyUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionSlaPolicyEntity policy = queryService.requirePolicy(id, audit);
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
        String escalateToPriority = queryService.normalizeCode(safeRequest.getEscalateToPriority());
        if (escalateToPriority != null) {
            queryService.validatePriority(escalateToPriority);
            policy.setEscalateToPriority(escalateToPriority);
        }
        if (safeRequest.getEnabled() != null) {
            policy.setEnabled(Boolean.TRUE.equals(safeRequest.getEnabled()) ? 1 : 0);
        }
        policy.setRemark(truncate(trimToNull(safeRequest.getRemark()), 512));
        touch(policy, audit);
        policyMapper.updateById(policy);
        return queryService.toResponse(policy);
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
    private void touch(ExceptionSlaPolicyEntity policy, AuditMetadata audit) {
        policy.setUpdatedBy(audit.userId());
        policy.setUpdatedTime(audit.now());
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
}
