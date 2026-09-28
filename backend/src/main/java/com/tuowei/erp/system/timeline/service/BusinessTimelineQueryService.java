package com.tuowei.erp.system.timeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.timeline.mapper.BusinessTimelineMapper;
import com.tuowei.erp.system.timeline.model.BusinessTimelineEntity;
import com.tuowei.erp.system.timeline.web.BusinessTimelineQuery;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Read side of business timelines: paged listing plus shared tenant/normalization/response helpers reused by the command service. */
@Service
public class BusinessTimelineQueryService {

    private final BusinessTimelineMapper timelineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BusinessTimelineQueryService(
            BusinessTimelineMapper timelineMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.timelineMapper = timelineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessTimelineResponse> list(BusinessTimelineQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        BusinessTimelineQuery safeQuery = query == null ? new BusinessTimelineQuery() : query;
        String businessType = normalizeBusinessType(safeQuery.getBusinessType());
        if (safeQuery.getBusinessId() == null && !StringUtils.hasText(safeQuery.getBusinessNo())) {
            throw new IllegalArgumentException("业务ID和业务单号不能同时为空");
        }
        Page<BusinessTimelineEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<BusinessTimelineEntity> result = timelineMapper.selectPage(page, buildQuery(audit, safeQuery, businessType));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    private LambdaQueryWrapper<BusinessTimelineEntity> buildQuery(
            AuditMetadata audit,
            BusinessTimelineQuery query,
            String businessType
    ) {
        LambdaQueryWrapper<BusinessTimelineEntity> wrapper = new LambdaQueryWrapper<BusinessTimelineEntity>()
                .eq(BusinessTimelineEntity::getCompanyId, audit.companyId())
                .eq(BusinessTimelineEntity::getAccountBookId, audit.accountBookId())
                .eq(BusinessTimelineEntity::getDeletedFlag, 0)
                .eq(BusinessTimelineEntity::getBusinessType, businessType);
        if (query.getBusinessId() != null) {
            wrapper.eq(BusinessTimelineEntity::getBusinessId, query.getBusinessId());
        }
        String businessNo = trimToNull(query.getBusinessNo());
        if (businessNo != null) {
            wrapper.eq(BusinessTimelineEntity::getBusinessNo, businessNo);
        }
        return wrapper.orderByDesc(BusinessTimelineEntity::getCreatedTime)
                .orderByDesc(BusinessTimelineEntity::getId);
    }

    BusinessTimelineResponse toResponse(BusinessTimelineEntity entity) {
        return new BusinessTimelineResponse(
                entity.getId(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getEventType(),
                entity.getContent(),
                entity.getAttachmentId(),
                entity.getOperatorUserId(),
                entity.getCreatedTime()
        );
    }

    String normalizeBusinessType(String value) {
        return requireText(value, "业务类型不能为空").toUpperCase(Locale.ROOT);
    }

    String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100);
    }
}
