package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.mapper.NotificationRecipientMapper;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import com.tuowei.erp.system.notification.model.NotificationRecipientEntity;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import com.tuowei.erp.system.notification.web.NotificationResponse;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String CATEGORY_TODO = "TODO";
    private static final String CATEGORY_NOTICE = "NOTICE";
    private static final String TYPE_WORKFLOW_PENDING = "WORKFLOW_APPROVAL_PENDING";
    private static final String TYPE_WORKFLOW_APPROVED = "WORKFLOW_APPROVED";
    private static final String TYPE_WORKFLOW_REJECTED = "WORKFLOW_REJECTED";

    private final NotificationMapper notificationMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final NotificationWebhookPublisher webhookPublisher;
    private final NotificationQueryService queryService;

    public NotificationService(
            NotificationMapper notificationMapper,
            NotificationRecipientMapper recipientMapper,
            AuditMetadataFactory auditMetadataFactory,
            NotificationWebhookPublisher webhookPublisher,
            NotificationQueryService queryService
    ) {
        this.notificationMapper = notificationMapper;
        this.recipientMapper = recipientMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.webhookPublisher = webhookPublisher;
        this.queryService = queryService;
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
        AuditMetadata audit = auditMetadataFactory.current();
        NotificationRecipientEntity recipient = queryService.requireMineRecipient(recipientId, audit);
        if (!Integer.valueOf(1).equals(recipient.getReadFlag())) {
            LocalDateTime now = audit.now();
            recipient.setReadFlag(1);
            recipient.setReadTime(now);
            recipient.setUpdatedBy(audit.userId());
            recipient.setUpdatedTime(now);
            recipientMapper.updateById(recipient);
        }
        NotificationEntity notification = queryService.requireNotification(recipient.getNotificationId(), audit);
        return queryService.toResponse(recipient, notification);
    }

    @Transactional
    public void markAllRead() {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        recipientMapper.update(null, new LambdaUpdateWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .eq(NotificationRecipientEntity::getRecipientUserId, audit.userId())
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE)
                .eq(NotificationRecipientEntity::getReadFlag, 0)
                .inSql(NotificationRecipientEntity::getNotificationId,
                        queryService.activeNotificationSubQuery(audit, null, null))
                .set(NotificationRecipientEntity::getReadFlag, 1)
                .set(NotificationRecipientEntity::getReadTime, now)
                .set(NotificationRecipientEntity::getUpdatedBy, audit.userId())
                .set(NotificationRecipientEntity::getUpdatedTime, now));
    }

    @Transactional
    public int markBatchRead(List<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return 0;
        }
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        List<Long> ids = recipientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return recipientMapper.update(null, new LambdaUpdateWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .eq(NotificationRecipientEntity::getRecipientUserId, audit.userId())
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE)
                .eq(NotificationRecipientEntity::getReadFlag, 0)
                .in(NotificationRecipientEntity::getId, ids)
                .set(NotificationRecipientEntity::getReadFlag, 1)
                .set(NotificationRecipientEntity::getReadTime, now)
                .set(NotificationRecipientEntity::getUpdatedBy, audit.userId())
                .set(NotificationRecipientEntity::getUpdatedTime, now));
    }

    @Transactional
    public void createWorkflowPending(
            WorkflowInstanceEntity instance,
            List<Long> recipientUserIds,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        if (recipientUserIds.isEmpty()) {
            return;
        }
        NotificationEntity notification = createNotification(
                CATEGORY_TODO,
                TYPE_WORKFLOW_PENDING,
                "待审批：" + instance.getTitle(),
                "单据 " + instance.getBusinessNo() + " 已提交审批，请及时处理",
                instance,
                workflowTargetUrl(instance),
                audit,
                now
        );
        createRecipients(notification.getId(), audit.companyId(), recipientUserIds, audit.userId(), now);
        try {
            webhookPublisher.publishWorkflowPending(notification, recipientUserIds);
        } catch (Exception ignored) {
            // webhook failures must not break notification creation
        }
    }

    @Transactional
    public void closeWorkflowPending(WorkflowInstanceEntity instance, AuditMetadata audit, LocalDateTime now) {
        List<NotificationEntity> notifications = activeWorkflowPendingNotifications(instance, audit);
        if (notifications.isEmpty()) {
            return;
        }
        List<Long> notificationIds = notifications.stream().map(NotificationEntity::getId).toList();
        recipientMapper.update(null, new LambdaUpdateWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .in(NotificationRecipientEntity::getNotificationId, notificationIds)
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE)
                .set(NotificationRecipientEntity::getStatus, STATUS_CLOSED)
                .set(NotificationRecipientEntity::getReadFlag, 1)
                .set(NotificationRecipientEntity::getReadTime, now)
                .set(NotificationRecipientEntity::getUpdatedBy, audit.userId())
                .set(NotificationRecipientEntity::getUpdatedTime, now));
    }

    @Transactional
    public void closeWorkflowPendingForUser(
            WorkflowInstanceEntity instance,
            Long recipientUserId,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<NotificationEntity> notifications = activeWorkflowPendingNotifications(instance, audit);
        if (notifications.isEmpty()) {
            return;
        }
        List<Long> notificationIds = notifications.stream().map(NotificationEntity::getId).toList();
        recipientMapper.update(null, new LambdaUpdateWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .in(NotificationRecipientEntity::getNotificationId, notificationIds)
                .eq(NotificationRecipientEntity::getRecipientUserId, recipientUserId)
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE)
                .set(NotificationRecipientEntity::getStatus, STATUS_CLOSED)
                .set(NotificationRecipientEntity::getReadFlag, 1)
                .set(NotificationRecipientEntity::getReadTime, now)
                .set(NotificationRecipientEntity::getUpdatedBy, audit.userId())
                .set(NotificationRecipientEntity::getUpdatedTime, now));
    }

    @Transactional
    public void notifyWorkflowResult(
            WorkflowInstanceEntity instance,
            String action,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        String type = switch (action) {
            case "APPROVE" -> TYPE_WORKFLOW_APPROVED;
            case "REJECT" -> TYPE_WORKFLOW_REJECTED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String title = "APPROVE".equals(action)
                ? "审批通过：" + instance.getBusinessNo()
                : "审批驳回：" + instance.getBusinessNo();
        String content = StringUtils.hasText(comment)
                ? comment.trim()
                : defaultWorkflowResultContent(action, instance.getTitle());
        NotificationEntity notification = createNotification(
                CATEGORY_NOTICE,
                type,
                title,
                content,
                instance,
                workflowTargetUrl(instance),
                audit,
                now
        );
        createRecipients(notification.getId(), audit.companyId(), List.of(instance.getSubmitUserId()), audit.userId(), now);
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
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        NotificationEntity notification = createNotification(
                category,
                notificationType,
                title,
                content,
                businessType,
                businessId,
                businessNo,
                targetUrl,
                audit,
                now
        );
        createRecipients(notification.getId(), audit.companyId(), recipientUserIds, audit.userId(), now);
    }

    private String defaultWorkflowResultContent(String action, String title) {
        return ("REJECT".equals(action) ? "审批驳回：" : "审批通过：") + title;
    }

    private NotificationEntity createNotification(
            String category,
            String notificationType,
            String title,
            String content,
            WorkflowInstanceEntity instance,
            String targetUrl,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        return createNotification(
                category,
                notificationType,
                title,
                content,
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getBusinessNo(),
                targetUrl,
                audit,
                now
        );
    }

    private NotificationEntity createNotification(
            String category,
            String notificationType,
            String title,
            String content,
            String businessType,
            Long businessId,
            String businessNo,
            String targetUrl,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        NotificationEntity notification = new NotificationEntity();
        notification.setCompanyId(audit.companyId());
        notification.setAccountBookId(audit.accountBookId());
        notification.setCategory(category);
        notification.setNotificationType(notificationType);
        notification.setTitle(truncate(title, 128));
        notification.setContent(truncate(content, 512));
        notification.setBusinessType(businessType);
        notification.setBusinessId(businessId);
        notification.setBusinessNo(businessNo);
        notification.setTargetUrl(targetUrl);
        notification.setStatus(STATUS_ACTIVE);
        notification.setDeletedFlag(0);
        notification.setCreatedBy(audit.userId());
        notification.setCreatedTime(now);
        notification.setUpdatedBy(audit.userId());
        notification.setUpdatedTime(now);
        notification.setVersion(0);
        notificationMapper.insert(notification);
        return notification;
    }

    private List<NotificationEntity> activeWorkflowPendingNotifications(
            WorkflowInstanceEntity instance,
            AuditMetadata audit
    ) {
        return notificationMapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getCompanyId, audit.companyId())
                .eq(NotificationEntity::getAccountBookId, audit.accountBookId())
                .eq(NotificationEntity::getBusinessType, instance.getBusinessType())
                .eq(NotificationEntity::getBusinessId, instance.getBusinessId())
                .eq(NotificationEntity::getCategory, CATEGORY_TODO)
                .eq(NotificationEntity::getNotificationType, TYPE_WORKFLOW_PENDING)
                .eq(NotificationEntity::getStatus, STATUS_ACTIVE)
                .eq(NotificationEntity::getDeletedFlag, 0));
    }

    private void createRecipients(
            Long notificationId,
            Long companyId,
            List<Long> recipientUserIds,
            Long operatorUserId,
            LocalDateTime now
    ) {
        for (Long userId : new LinkedHashSet<>(recipientUserIds)) {
            if (userId == null) {
                continue;
            }
            NotificationRecipientEntity recipient = new NotificationRecipientEntity();
            recipient.setCompanyId(companyId);
            recipient.setNotificationId(notificationId);
            recipient.setRecipientUserId(userId);
            recipient.setReadFlag(0);
            recipient.setStatus(STATUS_ACTIVE);
            recipient.setCreatedBy(operatorUserId);
            recipient.setCreatedTime(now);
            recipient.setUpdatedBy(operatorUserId);
            recipient.setUpdatedTime(now);
            recipient.setVersion(0);
            recipientMapper.insert(recipient);
        }
    }

    private String workflowTargetUrl(WorkflowInstanceEntity instance) {
        return "/workflow/tasks?businessType=%s&businessId=%s".formatted(instance.getBusinessType(), instance.getBusinessId());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
