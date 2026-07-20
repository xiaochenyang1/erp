package com.tuowei.erp.system.timeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.timeline.mapper.BusinessTimelineMapper;
import com.tuowei.erp.system.timeline.model.BusinessTimelineEntity;
import com.tuowei.erp.system.timeline.web.BusinessTimelineCommentRequest;
import com.tuowei.erp.system.timeline.web.BusinessTimelineQuery;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class BusinessTimelineService {

    private static final String EVENT_COMMENT = "COMMENT";
    private static final String EVENT_ATTACHMENT_UPLOADED = "ATTACHMENT_UPLOADED";
    private static final String EVENT_ATTACHMENT_DELETED = "ATTACHMENT_DELETED";

    private final BusinessTimelineMapper timelineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BusinessTimelineService(
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

    @Transactional
    public BusinessTimelineResponse createComment(BusinessTimelineCommentRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        BusinessTimelineCommentRequest safeRequest = request == null ? new BusinessTimelineCommentRequest() : request;
        String content = requireText(safeRequest.getContent(), "备注内容不能为空");
        BusinessTimelineEntity entity = create(
                audit,
                safeRequest.getBusinessType(),
                safeRequest.getBusinessId(),
                safeRequest.getBusinessNo(),
                EVENT_COMMENT,
                content,
                null
        );
        return toResponse(entity);
    }

    @Transactional
    public void recordAttachmentUploaded(AttachmentEntity attachment, AuditMetadata audit) {
        if (attachment == null || audit == null) {
            return;
        }
        create(
                audit,
                attachment.getBusinessType(),
                attachment.getBusinessId(),
                attachment.getBusinessNo(),
                EVENT_ATTACHMENT_UPLOADED,
                "上传附件：" + requireText(attachment.getOriginalFilename(), "附件名称不能为空"),
                attachment.getId()
        );
    }

    @Transactional
    public void recordAttachmentDeleted(AttachmentEntity attachment, AuditMetadata audit) {
        if (attachment == null || audit == null) {
            return;
        }
        create(
                audit,
                attachment.getBusinessType(),
                attachment.getBusinessId(),
                attachment.getBusinessNo(),
                EVENT_ATTACHMENT_DELETED,
                "删除附件：" + requireText(attachment.getOriginalFilename(), "附件名称不能为空"),
                attachment.getId()
        );
    }

    private BusinessTimelineEntity create(
            AuditMetadata audit,
            String businessType,
            Long businessId,
            String businessNo,
            String eventType,
            String content,
            Long attachmentId
    ) {
        String normalizedBusinessType = normalizeBusinessType(businessType);
        if (businessId == null) {
            throw new IllegalArgumentException("业务ID不能为空");
        }
        String normalizedContent = truncate(requireText(content, "时间线内容不能为空"), 1024);
        LocalDateTime now = audit.now();
        BusinessTimelineEntity entity = new BusinessTimelineEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setBusinessType(normalizedBusinessType);
        entity.setBusinessId(businessId);
        entity.setBusinessNo(truncate(trimToNull(businessNo), 128));
        entity.setEventType(eventType);
        entity.setContent(normalizedContent);
        entity.setAttachmentId(attachmentId);
        entity.setOperatorUserId(audit.userId());
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        timelineMapper.insert(entity);
        return entity;
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

    private BusinessTimelineResponse toResponse(BusinessTimelineEntity entity) {
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

    private String normalizeBusinessType(String value) {
        return requireText(value, "业务类型不能为空").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
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
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100);
    }
}
