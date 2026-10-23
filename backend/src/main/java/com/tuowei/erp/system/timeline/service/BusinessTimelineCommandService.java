package com.tuowei.erp.system.timeline.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.timeline.mapper.BusinessTimelineMapper;
import com.tuowei.erp.system.timeline.model.BusinessTimelineEntity;
import com.tuowei.erp.system.timeline.web.BusinessTimelineCommentRequest;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Write side of business timelines: create manual comments and record attachment upload/delete events, reusing the query service's shared helpers. */
@Service
public class BusinessTimelineCommandService {

    private static final String EVENT_COMMENT = "COMMENT";
    private static final String EVENT_ATTACHMENT_UPLOADED = "ATTACHMENT_UPLOADED";
    private static final String EVENT_ATTACHMENT_DELETED = "ATTACHMENT_DELETED";

    private final BusinessTimelineMapper timelineMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final BusinessTimelineQueryService queryService;

    public BusinessTimelineCommandService(
            BusinessTimelineMapper timelineMapper,
            AuditMetadataFactory auditMetadataFactory,
            BusinessTimelineQueryService queryService
    ) {
        this.timelineMapper = timelineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public BusinessTimelineResponse createComment(BusinessTimelineCommentRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        BusinessTimelineCommentRequest safeRequest = request == null ? new BusinessTimelineCommentRequest() : request;
        String content = queryService.requireText(safeRequest.getContent(), "备注内容不能为空");
        BusinessTimelineEntity entity = create(
                audit,
                safeRequest.getBusinessType(),
                safeRequest.getBusinessId(),
                safeRequest.getBusinessNo(),
                EVENT_COMMENT,
                content,
                null
        );
        return queryService.toResponse(entity);
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
                "上传附件：" + queryService.requireText(attachment.getOriginalFilename(), "附件名称不能为空"),
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
                "删除附件：" + queryService.requireText(attachment.getOriginalFilename(), "附件名称不能为空"),
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
        String normalizedBusinessType = queryService.normalizeBusinessType(businessType);
        if (businessId == null) {
            throw new IllegalArgumentException("业务ID不能为空");
        }
        String normalizedContent = truncate(queryService.requireText(content, "时间线内容不能为空"), 1024);
        LocalDateTime now = audit.now();
        BusinessTimelineEntity entity = new BusinessTimelineEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setBusinessType(normalizedBusinessType);
        entity.setBusinessId(businessId);
        entity.setBusinessNo(truncate(queryService.trimToNull(businessNo), 128));
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
