package com.tuowei.erp.system.timeline.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.timeline.web.BusinessTimelineCommentRequest;
import com.tuowei.erp.system.timeline.web.BusinessTimelineQuery;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade delegating business-timeline listing to the query service and comment/attachment event recording to the command service. */
@Service
public class BusinessTimelineService {

    private final BusinessTimelineQueryService queryService;
    private final BusinessTimelineCommandService commandService;

    public BusinessTimelineService(BusinessTimelineQueryService queryService, BusinessTimelineCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessTimelineResponse> list(BusinessTimelineQuery query) {
        return queryService.list(query);
    }

    @Transactional
    public BusinessTimelineResponse createComment(BusinessTimelineCommentRequest request) {
        return commandService.createComment(request);
    }

    @Transactional
    public void recordAttachmentUploaded(AttachmentEntity attachment, AuditMetadata audit) {
        commandService.recordAttachmentUploaded(attachment, audit);
    }

    @Transactional
    public void recordAttachmentDeleted(AttachmentEntity attachment, AuditMetadata audit) {
        commandService.recordAttachmentDeleted(attachment, audit);
    }
}
