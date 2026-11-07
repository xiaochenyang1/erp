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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Tenant-scoped rule and rule-hit queries plus response mapping. */
@Service
public class ExceptionRuleQueryService {

    static final int DEFAULT_SCHEDULE_INTERVAL_MINUTES = 60;
    static final int MIN_SCHEDULE_INTERVAL_MINUTES = 5;
    static final int MAX_SCHEDULE_INTERVAL_MINUTES = 10080;

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionRuleMapper ruleMapper;
    private final ExceptionRuleHitMapper hitMapper;

    public ExceptionRuleQueryService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleHitMapper hitMapper
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.ruleMapper = ruleMapper;
        this.hitMapper = hitMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionRuleResponse> list(ExceptionRulePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRulePageQuery safeQuery = query == null ? new ExceptionRulePageQuery() : query;
        Page<ExceptionRuleEntity> result = ruleMapper.selectPage(
                new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize())),
                buildRuleQuery(audit, safeQuery)
        );
        return new PageResponse<>(
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toRuleResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionRuleHitResponse> listHits(ExceptionRuleHitPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionRuleHitPageQuery safeQuery = query == null ? new ExceptionRuleHitPageQuery() : query;
        Page<ExceptionRuleHitEntity> result = hitMapper.selectPage(
                new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize())),
                buildHitQuery(audit, safeQuery)
        );
        return new PageResponse<>(
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toHitResponse).toList()
        );
    }

    ExceptionRuleResponse toRuleResponse(ExceptionRuleEntity rule) {
        return new ExceptionRuleResponse(
                rule.getId(), rule.getRuleCode(), rule.getRuleName(), rule.getRuleType(), rule.getCategory(),
                rule.getPriority(), rule.getThresholdValue(), rule.getThresholdUnit(),
                Integer.valueOf(1).equals(rule.getEnabled()), rule.getAssigneeUserId(),
                scheduleIntervalMinutes(rule), rule.getNextScanTime(), rule.getRemark(), rule.getLastScanTime(),
                rule.getLastScanStatus(), rule.getLastHitCount(), rule.getLastTicketCreatedCount(),
                rule.getLastErrorMessage(), rule.getUpdatedTime()
        );
    }

    private LambdaQueryWrapper<ExceptionRuleEntity> buildRuleQuery(AuditMetadata audit, ExceptionRulePageQuery query) {
        LambdaQueryWrapper<ExceptionRuleEntity> wrapper = new LambdaQueryWrapper<ExceptionRuleEntity>()
                .eq(ExceptionRuleEntity::getCompanyId, audit.companyId())
                .eq(ExceptionRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionRuleEntity::getDeletedFlag, 0);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ExceptionRuleEntity::getRuleCode, keyword)
                    .or().like(ExceptionRuleEntity::getRuleName, keyword)
                    .or().like(ExceptionRuleEntity::getRemark, keyword));
        }
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) wrapper.eq(ExceptionRuleEntity::getRuleType, ruleType);
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
        if (query.getRuleId() != null) wrapper.eq(ExceptionRuleHitEntity::getRuleId, query.getRuleId());
        String ruleType = normalizeCode(query.getRuleType());
        if (ruleType != null) wrapper.eq(ExceptionRuleHitEntity::getRuleType, ruleType);
        String sourceNo = trimToNull(query.getSourceNo());
        if (sourceNo != null) wrapper.like(ExceptionRuleHitEntity::getSourceNo, sourceNo);
        if (query.getTicketId() != null) wrapper.eq(ExceptionRuleHitEntity::getTicketId, query.getTicketId());
        return wrapper.orderByDesc(ExceptionRuleHitEntity::getLastHitTime).orderByDesc(ExceptionRuleHitEntity::getId);
    }

    private ExceptionRuleHitResponse toHitResponse(ExceptionRuleHitEntity hit) {
        return new ExceptionRuleHitResponse(
                hit.getId(), hit.getRuleId(), hit.getRuleCode(), hit.getRuleType(), hit.getSourceType(),
                hit.getSourceId(), hit.getSourceNo(), hit.getSourceRoute(), hit.getHitKey(), hit.getTitle(),
                hit.getDescription(), hit.getTriggerValue(), hit.getThresholdValue(), hit.getTicketId(),
                hit.getHitCount(), hit.getFirstHitTime(), hit.getLastHitTime()
        );
    }

    private int scheduleIntervalMinutes(ExceptionRuleEntity rule) {
        Integer interval = rule.getScheduleIntervalMinutes();
        if (interval == null) return DEFAULT_SCHEDULE_INTERVAL_MINUTES;
        return Math.max(MIN_SCHEDULE_INTERVAL_MINUTES, Math.min(MAX_SCHEDULE_INTERVAL_MINUTES, interval));
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) { return pageNo == null || pageNo < 1 ? 1L : pageNo; }
    private long normalizePageSize(Integer pageSize) { return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200); }
}
