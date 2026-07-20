package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public NotificationService(
            NotificationMapper notificationMapper,
            NotificationRecipientMapper recipientMapper,
            AuditMetadataFactory auditMetadataFactory,
            NotificationWebhookPublisher webhookPublisher
    ) {
        this.notificationMapper = notificationMapper;
        this.recipientMapper = recipientMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.webhookPublisher = webhookPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(NotificationPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        NotificationPageQuery safeQuery = query == null ? new NotificationPageQuery() : query;
        Page<NotificationRecipientEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<NotificationRecipientEntity> result = recipientMapper.selectPage(page, buildRecipientQuery(audit, safeQuery));
        Map<Long, NotificationEntity> notifications = loadNotifications(result.getRecords(), audit);
        List<NotificationResponse> records = result.getRecords().stream()
                .map(recipient -> toResponse(recipient, notifications.get(recipient.getNotificationId())))
                .filter(Objects::nonNull)
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional(readOnly = true)
    public long countUnreadMine() {
        AuditMetadata audit = auditMetadataFactory.current();
        return recipientMapper.selectCount(baseMineRecipientQuery(audit)
                .eq(NotificationRecipientEntity::getReadFlag, 0)
                .inSql(NotificationRecipientEntity::getNotificationId, activeNotificationSubQuery(audit, null, null)));
    }

    @Transactional
    public NotificationResponse markRead(Long recipientId) {
        AuditMetadata audit = auditMetadataFactory.current();
        NotificationRecipientEntity recipient = recipientMapper.selectOne(baseMineRecipientQuery(audit)
                .eq(NotificationRecipientEntity::getId, recipientId));
        if (recipient == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!Integer.valueOf(1).equals(recipient.getReadFlag())) {
            LocalDateTime now = audit.now();
            recipient.setReadFlag(1);
            recipient.setReadTime(now);
            recipient.setUpdatedBy(audit.userId());
            recipient.setUpdatedTime(now);
            recipientMapper.updateById(recipient);
        }
        NotificationEntity notification = requireNotification(recipient.getNotificationId(), audit);
        return toResponse(recipient, notification);
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
                .inSql(NotificationRecipientEntity::getNotificationId, activeNotificationSubQuery(audit, null, null))
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

    private LambdaQueryWrapper<NotificationRecipientEntity> buildRecipientQuery(AuditMetadata audit, NotificationPageQuery query) {
        LambdaQueryWrapper<NotificationRecipientEntity> wrapper = baseMineRecipientQuery(audit);
        if (Boolean.TRUE.equals(query.getUnreadOnly())) {
            wrapper.eq(NotificationRecipientEntity::getReadFlag, 0);
        }
        wrapper.inSql(NotificationRecipientEntity::getNotificationId,
                activeNotificationSubQuery(audit, normalizeCode(query.getCategory()), normalizeCode(query.getNotificationType())));
        return wrapper.orderByDesc(NotificationRecipientEntity::getCreatedTime).orderByDesc(NotificationRecipientEntity::getId);
    }

    private LambdaQueryWrapper<NotificationRecipientEntity> baseMineRecipientQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .eq(NotificationRecipientEntity::getRecipientUserId, audit.userId())
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE);
    }

    private String activeNotificationSubQuery(AuditMetadata audit, String category, String notificationType) {
        StringBuilder sql = new StringBuilder("select id from sys_notification where deleted_flag = 0 and status = 'ACTIVE'")
                .append(" and company_id = ").append(audit.companyId())
                .append(" and account_book_id = ").append(audit.accountBookId());
        if (StringUtils.hasText(category)) {
            sql.append(" and category = '").append(escapeSql(category)).append("'");
        }
        if (StringUtils.hasText(notificationType)) {
            sql.append(" and notification_type = '").append(escapeSql(notificationType)).append("'");
        }
        return sql.toString();
    }

    private Map<Long, NotificationEntity> loadNotifications(List<NotificationRecipientEntity> recipients, AuditMetadata audit) {
        List<Long> notificationIds = recipients.stream()
                .map(NotificationRecipientEntity::getNotificationId)
                .distinct()
                .toList();
        if (notificationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return notificationMapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
                        .eq(NotificationEntity::getCompanyId, audit.companyId())
                        .eq(NotificationEntity::getAccountBookId, audit.accountBookId())
                        .eq(NotificationEntity::getDeletedFlag, 0)
                        .eq(NotificationEntity::getStatus, STATUS_ACTIVE)
                        .in(NotificationEntity::getId, notificationIds))
                .stream()
                .collect(Collectors.toMap(NotificationEntity::getId, Function.identity()));
    }

    private NotificationEntity requireNotification(Long notificationId, AuditMetadata audit) {
        NotificationEntity notification = notificationMapper.selectOne(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getCompanyId, audit.companyId())
                .eq(NotificationEntity::getAccountBookId, audit.accountBookId())
                .eq(NotificationEntity::getDeletedFlag, 0)
                .eq(NotificationEntity::getStatus, STATUS_ACTIVE)
                .eq(NotificationEntity::getId, notificationId));
        if (notification == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        return notification;
    }

    private NotificationResponse toResponse(NotificationRecipientEntity recipient, NotificationEntity notification) {
        if (notification == null) {
            return null;
        }
        return new NotificationResponse(
                recipient.getId(),
                notification.getId(),
                notification.getCategory(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getBusinessType(),
                notification.getBusinessId(),
                notification.getBusinessNo(),
                notification.getTargetUrl(),
                Integer.valueOf(1).equals(recipient.getReadFlag()),
                recipient.getReadTime(),
                notification.getCreatedTime()
        );
    }

    private String workflowTargetUrl(WorkflowInstanceEntity instance) {
        return "/workflow/tasks?businessType=%s&businessId=%s".formatted(instance.getBusinessType(), instance.getBusinessId());
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
