package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-side filtering, tenant guards and response hydration for user notifications. */
@Service
public class NotificationQueryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final NotificationMapper notificationMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public NotificationQueryService(
            NotificationMapper notificationMapper,
            NotificationRecipientMapper recipientMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.notificationMapper = notificationMapper;
        this.recipientMapper = recipientMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(NotificationPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        NotificationPageQuery safeQuery = query == null ? new NotificationPageQuery() : query;
        Page<NotificationRecipientEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<NotificationRecipientEntity> result = recipientMapper.selectPage(
                page,
                buildRecipientQuery(audit, safeQuery)
        );
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
                .inSql(NotificationRecipientEntity::getNotificationId,
                        activeNotificationSubQuery(audit, null, null)));
    }

    NotificationRecipientEntity requireMineRecipient(Long recipientId, AuditMetadata audit) {
        NotificationRecipientEntity recipient = recipientMapper.selectOne(baseMineRecipientQuery(audit)
                .eq(NotificationRecipientEntity::getId, recipientId));
        if (recipient == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        return recipient;
    }

    NotificationEntity requireNotification(Long notificationId, AuditMetadata audit) {
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

    NotificationResponse toResponse(NotificationRecipientEntity recipient, NotificationEntity notification) {
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

    String activeNotificationSubQuery(
            AuditMetadata audit,
            String category,
            String notificationType
    ) {
        StringBuilder sql = new StringBuilder(
                "select id from sys_notification where deleted_flag = 0 and status = 'ACTIVE'"
        )
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

    private LambdaQueryWrapper<NotificationRecipientEntity> buildRecipientQuery(
            AuditMetadata audit,
            NotificationPageQuery query
    ) {
        LambdaQueryWrapper<NotificationRecipientEntity> wrapper = baseMineRecipientQuery(audit);
        if (Boolean.TRUE.equals(query.getUnreadOnly())) {
            wrapper.eq(NotificationRecipientEntity::getReadFlag, 0);
        }
        wrapper.inSql(
                NotificationRecipientEntity::getNotificationId,
                activeNotificationSubQuery(
                        audit,
                        normalizeCode(query.getCategory()),
                        normalizeCode(query.getNotificationType())
                )
        );
        return wrapper.orderByDesc(NotificationRecipientEntity::getCreatedTime)
                .orderByDesc(NotificationRecipientEntity::getId);
    }

    private LambdaQueryWrapper<NotificationRecipientEntity> baseMineRecipientQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<NotificationRecipientEntity>()
                .eq(NotificationRecipientEntity::getCompanyId, audit.companyId())
                .eq(NotificationRecipientEntity::getRecipientUserId, audit.userId())
                .eq(NotificationRecipientEntity::getStatus, STATUS_ACTIVE);
    }

    private Map<Long, NotificationEntity> loadNotifications(
            List<NotificationRecipientEntity> recipients,
            AuditMetadata audit
    ) {
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

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
