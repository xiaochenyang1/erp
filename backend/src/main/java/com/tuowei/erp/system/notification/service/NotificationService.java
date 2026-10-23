package com.tuowei.erp.system.notification.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import com.tuowei.erp.system.notification.web.NotificationResponse;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compatibility facade over the read-side {@link NotificationQueryService} and the write-side
 * {@link NotificationCommandService}. Every public method delegates to the owning collaborator while
 * preserving the original transaction semantics, so the existing callers keep a single injection point.
 */
@Service
public class NotificationService {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    public NotificationService(
            NotificationQueryService queryService,
            NotificationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(NotificationPageQuery query) {
        NotificationPageQuery safeQuery = query == null ? new NotificationPageQuery() : query;
        return queryService.listMine(safeQuery);
    }

    @Transactional(readOnly = true)
    public long countUnreadMine() {
        return queryService.countUnreadMine();
    }

    @Transactional
    public NotificationResponse markRead(Long recipientId) {
        return commandService.markRead(recipientId);
    }

    @Transactional
    public void markAllRead() {
        commandService.markAllRead();
    }

    @Transactional
    public int markBatchRead(List<Long> recipientIds) {
        return commandService.markBatchRead(recipientIds);
    }

    @Transactional
    public void createWorkflowPending(
            WorkflowInstanceEntity instance,
            List<Long> recipientUserIds,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        commandService.createWorkflowPending(instance, recipientUserIds, audit, now);
    }

    @Transactional
    public void closeWorkflowPending(WorkflowInstanceEntity instance, AuditMetadata audit, LocalDateTime now) {
        commandService.closeWorkflowPending(instance, audit, now);
    }

    @Transactional
    public void closeWorkflowPendingForUser(
            WorkflowInstanceEntity instance,
            Long recipientUserId,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        commandService.closeWorkflowPendingForUser(instance, recipientUserId, audit, now);
    }

    @Transactional
    public void notifyWorkflowResult(
            WorkflowInstanceEntity instance,
            String action,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        commandService.notifyWorkflowResult(instance, action, comment, audit, now);
    }

    @Transactional
    public void createBusinessNotification(
            String category,
            String notificationType,
            String title,
            String content,
            String businessType,
            Long businessId,
            String businessNo,
            String targetUrl,
            List<Long> recipientUserIds,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        commandService.createBusinessNotification(
                category,
                notificationType,
                title,
                content,
                businessType,
                businessId,
                businessNo,
                targetUrl,
                recipientUserIds,
                audit,
                now
        );
    }

    @Transactional(noRollbackFor = DuplicateKeyException.class)
    public boolean createBusinessNotificationIfAbsent(
            String category,
            String notificationType,
            String title,
            String content,
            String businessType,
            Long businessId,
            String businessNo,
            String targetUrl,
            List<Long> recipientUserIds,
            String dedupKey,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        return commandService.createBusinessNotificationIfAbsent(
                category,
                notificationType,
                title,
                content,
                businessType,
                businessId,
                businessNo,
                targetUrl,
                recipientUserIds,
                dedupKey,
                audit,
                now
        );
    }
}
