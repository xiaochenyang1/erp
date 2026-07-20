package com.tuowei.erp.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEventEntity;
import com.tuowei.erp.issue.sla.service.ExceptionSlaEscalationPolicy;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketEventResponse;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import com.tuowei.erp.system.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ExceptionTicketService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String DEFAULT_CATEGORY = "GENERAL";
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    private static final long SYSTEM_USER_ID = 0L;
    private static final String NOTIFICATION_BUSINESS_TYPE = "EXCEPTION_TICKET";
    private static final DateTimeFormatter TICKET_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionTicketMapper ticketMapper;
    private final ExceptionTicketEventMapper eventMapper;
    private final NotificationService notificationService;
    private final ExceptionSlaPolicyService slaPolicyService;
    private final Clock clock;
    private final AtomicLong ticketNoCounter = new AtomicLong();

    public ExceptionTicketService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionTicketMapper ticketMapper,
            ExceptionTicketEventMapper eventMapper,
            NotificationService notificationService,
            ExceptionSlaPolicyService slaPolicyService,
            Clock clock
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.notificationService = notificationService;
        this.slaPolicyService = slaPolicyService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionTicketResponse> list(ExceptionTicketPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketPageQuery safeQuery = query == null ? new ExceptionTicketPageQuery() : query;
        Page<ExceptionTicketEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<ExceptionTicketEntity> result = ticketMapper.selectPage(page, buildTicketQuery(audit, safeQuery));
        Map<Long, List<ExceptionTicketEventEntity>> events = loadEvents(result.getRecords(), audit);
        List<ExceptionTicketResponse> records = result.getRecords().stream()
                .map(ticket -> toResponse(ticket, events.getOrDefault(ticket.getId(), List.of())))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        return create(request, audit);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request, AuditMetadata audit) {
        ExceptionTicketCreateRequest safeRequest = request == null ? new ExceptionTicketCreateRequest() : request;
        String title = requireText(safeRequest.getTitle(), "异常标题不能为空");
        LocalDateTime now = audit.now();

        ExceptionTicketEntity ticket = new ExceptionTicketEntity();
        ticket.setCompanyId(audit.companyId());
        ticket.setAccountBookId(audit.accountBookId());
        ticket.setTicketNo(nextTicketNo(audit));
        ticket.setCategory(normalizeCodeOrDefault(safeRequest.getCategory(), DEFAULT_CATEGORY));
        ticket.setPriority(normalizeCodeOrDefault(safeRequest.getPriority(), DEFAULT_PRIORITY));
        ticket.setTitle(truncate(title, 128));
        ticket.setDescription(truncate(trimToNull(safeRequest.getDescription()), 1024));
        ticket.setSourceType(normalizeCode(safeRequest.getSourceType()));
        ticket.setSourceId(safeRequest.getSourceId());
        ticket.setSourceNo(truncate(trimToNull(safeRequest.getSourceNo()), 128));
        ticket.setSourceRoute(truncate(trimToNull(safeRequest.getSourceRoute()), 512));
        ticket.setStatus(STATUS_OPEN);
        ticket.setAssigneeUserId(safeRequest.getAssigneeUserId());
        ticket.setDueTime(safeRequest.getDueTime());
        ticket.setDeletedFlag(0);
        ticket.setCreatedBy(audit.userId());
        ticket.setCreatedTime(now);
        ticket.setUpdatedBy(audit.userId());
        ticket.setUpdatedTime(now);
        ticket.setVersion(0);
        ticketMapper.insert(ticket);

        ExceptionTicketEventEntity event = createEvent(ticket, "CREATE", null, STATUS_OPEN, "创建异常工单", audit, now);
        notifyTicket(
                "TODO",
                "EXCEPTION_TICKET_CREATED",
                "异常工单：" + ticket.getTicketNo(),
                "异常工单 " + ticket.getTitle() + " 已创建，请及时处理",
                ticket,
                recipients(ticket.getAssigneeUserId()),
                audit,
                now
        );
        return toResponse(ticket, List.of(event));
    }

    @Transactional
    public ExceptionTicketResponse assign(Long id, ExceptionTicketAssignRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketEntity ticket = requireTicket(id, audit);
        rejectClosed(ticket);
        LocalDateTime now = audit.now();
        Long assigneeUserId = request == null ? null : request.getAssigneeUserId();
        ticket.setAssigneeUserId(assigneeUserId);
        touch(ticket, audit, now);
        ticketMapper.updateById(ticket);
        ExceptionTicketEventEntity event = createEvent(ticket, "ASSIGN", ticket.getStatus(), ticket.getStatus(),
                commentOrDefault(request == null ? null : request.getComment(), "分派异常工单"), audit, now);
        notifyTicket(
                "TODO",
                "EXCEPTION_TICKET_ASSIGNED",
                "异常工单已分派：" + ticket.getTicketNo(),
                "异常工单 " + ticket.getTitle() + " 已分派给你",
                ticket,
                recipients(ticket.getAssigneeUserId()),
                audit,
                now
        );
        return toResponse(ticket, List.of(event));
    }

    @Transactional
    public ExceptionTicketResponse start(Long id, ExceptionTicketActionRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketEntity ticket = requireTicket(id, audit);
        rejectClosed(ticket);
        if (STATUS_PROCESSING.equals(ticket.getStatus())) {
            return transition(ticket, STATUS_PROCESSING, "START",
                    commentOrDefault(request == null ? null : request.getComment(), "开始处理异常工单"), audit);
        }
        if (!STATUS_OPEN.equals(ticket.getStatus())) {
            throw new IllegalArgumentException("只有待处理的异常工单可以开始处理");
        }
        return transition(ticket, STATUS_PROCESSING, "START",
                commentOrDefault(request == null ? null : request.getComment(), "开始处理异常工单"), audit);
    }

    @Transactional
    public ExceptionTicketResponse resolve(Long id, ExceptionTicketActionRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketEntity ticket = requireTicket(id, audit);
        rejectClosed(ticket);
        if (STATUS_RESOLVED.equals(ticket.getStatus())) {
            return transition(ticket, STATUS_RESOLVED, "RESOLVE",
                    commentOrDefault(request == null ? null : request.getComment(), "解决异常工单"), audit);
        }
        if (!STATUS_OPEN.equals(ticket.getStatus()) && !STATUS_PROCESSING.equals(ticket.getStatus())) {
            throw new IllegalArgumentException("只有待处理或处理中的异常工单可以解决");
        }
        LocalDateTime now = audit.now();
        String comment = commentOrDefault(request == null ? null : request.getComment(), "解决异常工单");
        String fromStatus = ticket.getStatus();
        ticket.setStatus(STATUS_RESOLVED);
        ticket.setResolution(truncate(comment, 512));
        ticket.setResolvedBy(audit.userId());
        ticket.setResolvedTime(now);
        touch(ticket, audit, now);
        ticketMapper.updateById(ticket);
        ExceptionTicketEventEntity event = createEvent(ticket, "RESOLVE", fromStatus, STATUS_RESOLVED, comment, audit, now);
        notifyTicketAction(ticket, "RESOLVE", comment, audit, now);
        return toResponse(ticket, List.of(event));
    }

    @Transactional
    public ExceptionTicketResponse close(Long id, ExceptionTicketActionRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketEntity ticket = requireTicket(id, audit);
        if (!STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new IllegalArgumentException("只有已解决的异常工单可以关闭");
        }
        LocalDateTime now = audit.now();
        String comment = commentOrDefault(request == null ? null : request.getComment(), "关闭异常工单");
        String fromStatus = ticket.getStatus();
        ticket.setStatus(STATUS_CLOSED);
        ticket.setClosedBy(audit.userId());
        ticket.setClosedTime(now);
        touch(ticket, audit, now);
        ticketMapper.updateById(ticket);
        ExceptionTicketEventEntity event = createEvent(ticket, "CLOSE", fromStatus, STATUS_CLOSED, comment, audit, now);
        notifyTicketAction(ticket, "CLOSE", comment, audit, now);
        return toResponse(ticket, List.of(event));
    }

    private ExceptionTicketResponse transition(
            ExceptionTicketEntity ticket,
            String toStatus,
            String action,
            String comment,
            AuditMetadata audit
    ) {
        LocalDateTime now = audit.now();
        String fromStatus = ticket.getStatus();
        ticket.setStatus(toStatus);
        touch(ticket, audit, now);
        ticketMapper.updateById(ticket);
        ExceptionTicketEventEntity event = createEvent(ticket, action, fromStatus, toStatus, comment, audit, now);
        notifyTicketAction(ticket, action, comment, audit, now);
        return toResponse(ticket, List.of(event));
    }

    @Transactional
    public int escalateOverdueTickets(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now(clock) : now;
        List<ExceptionTicketEntity> tickets = ticketMapper.selectList(new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getDeletedFlag, 0)
                .in(ExceptionTicketEntity::getStatus, List.of(STATUS_OPEN, STATUS_PROCESSING))
                .isNotNull(ExceptionTicketEntity::getDueTime)
                .le(ExceptionTicketEntity::getDueTime, effectiveNow)
                .orderByAsc(ExceptionTicketEntity::getDueTime)
                .orderByAsc(ExceptionTicketEntity::getId));
        Set<Long> escalatedTicketIds = escalatedTicketIds(tickets);
        int count = 0;
        for (ExceptionTicketEntity ticket : tickets) {
            if (ticket.getId() == null || escalatedTicketIds.contains(ticket.getId())) {
                continue;
            }
            AuditMetadata audit = systemAudit(ticket, effectiveNow);
            String fromPriority = normalizeCodeOrDefault(ticket.getPriority(), DEFAULT_PRIORITY);
            ExceptionSlaEscalationPolicy escalationPolicy = slaPolicyService.resolveEscalation(ticket, audit);
            if (!escalationPolicy.enabled()) {
                continue;
            }
            String toPriority = normalizeCodeOrDefault(escalationPolicy.targetPriority(), fromPriority);
            ticket.setPriority(toPriority);
            touch(ticket, audit, effectiveNow);
            ticketMapper.updateById(ticket);
            String comment = "异常工单已超时，优先级从 " + fromPriority + " 升级为 " + toPriority;
            createEvent(ticket, "ESCALATE", ticket.getStatus(), ticket.getStatus(), comment, audit, effectiveNow);
            notifyTicket(
                    "TODO",
                    "EXCEPTION_TICKET_ESCALATED",
                    "异常工单超时：" + ticket.getTicketNo(),
                    comment,
                    ticket,
                    ticketRecipients(ticket),
                    audit,
                    effectiveNow
            );
            count++;
        }
        return count;
    }

    private LambdaQueryWrapper<ExceptionTicketEntity> buildTicketQuery(AuditMetadata audit, ExceptionTicketPageQuery query) {
        LambdaQueryWrapper<ExceptionTicketEntity> wrapper = new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, audit.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionTicketEntity::getDeletedFlag, 0);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested
                    .like(ExceptionTicketEntity::getTicketNo, keyword)
                    .or()
                    .like(ExceptionTicketEntity::getTitle, keyword)
                    .or()
                    .like(ExceptionTicketEntity::getDescription, keyword));
        }
        eqIfText(wrapper, ExceptionTicketEntity::getStatus, normalizeCode(query.getStatus()));
        eqIfText(wrapper, ExceptionTicketEntity::getPriority, normalizeCode(query.getPriority()));
        eqIfText(wrapper, ExceptionTicketEntity::getCategory, normalizeCode(query.getCategory()));
        if (query.getAssigneeUserId() != null) {
            wrapper.eq(ExceptionTicketEntity::getAssigneeUserId, query.getAssigneeUserId());
        }
        String sourceNo = trimToNull(query.getSourceNo());
        if (sourceNo != null) {
            wrapper.like(ExceptionTicketEntity::getSourceNo, sourceNo);
        }
        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            wrapper.le(ExceptionTicketEntity::getDueTime, LocalDateTime.now(clock))
                    .in(ExceptionTicketEntity::getStatus, List.of(STATUS_OPEN, STATUS_PROCESSING));
        }
        return wrapper.orderByDesc(ExceptionTicketEntity::getUpdatedTime).orderByDesc(ExceptionTicketEntity::getId);
    }

    private void eqIfText(
            LambdaQueryWrapper<ExceptionTicketEntity> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<ExceptionTicketEntity, ?> column,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            wrapper.eq(column, value);
        }
    }

    private Map<Long, List<ExceptionTicketEventEntity>> loadEvents(List<ExceptionTicketEntity> tickets, AuditMetadata audit) {
        List<Long> ticketIds = tickets.stream()
                .map(ExceptionTicketEntity::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return eventMapper.selectList(new LambdaQueryWrapper<ExceptionTicketEventEntity>()
                        .eq(ExceptionTicketEventEntity::getCompanyId, audit.companyId())
                        .eq(ExceptionTicketEventEntity::getAccountBookId, audit.accountBookId())
                        .in(ExceptionTicketEventEntity::getTicketId, ticketIds)
                        .orderByAsc(ExceptionTicketEventEntity::getCreatedTime)
                        .orderByAsc(ExceptionTicketEventEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(ExceptionTicketEventEntity::getTicketId));
    }

    private ExceptionTicketEntity requireTicket(Long id, AuditMetadata audit) {
        ExceptionTicketEntity ticket = ticketMapper.selectOne(new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, audit.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionTicketEntity::getDeletedFlag, 0)
                .eq(ExceptionTicketEntity::getId, id));
        if (ticket == null) {
            throw new IllegalArgumentException("异常工单不存在");
        }
        return ticket;
    }

    private ExceptionTicketEventEntity createEvent(
            ExceptionTicketEntity ticket,
            String action,
            String fromStatus,
            String toStatus,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        ExceptionTicketEventEntity event = new ExceptionTicketEventEntity();
        event.setCompanyId(audit.companyId());
        event.setAccountBookId(audit.accountBookId());
        event.setTicketId(ticket.getId());
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setComment(truncate(comment, 512));
        event.setOperatorUserId(audit.userId());
        event.setCreatedTime(now);
        eventMapper.insert(event);
        return event;
    }

    private Set<Long> escalatedTicketIds(List<ExceptionTicketEntity> tickets) {
        List<Long> ticketIds = tickets.stream()
                .map(ExceptionTicketEntity::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ticketIds.isEmpty()) {
            return Set.of();
        }
        return eventMapper.selectList(new LambdaQueryWrapper<ExceptionTicketEventEntity>()
                        .in(ExceptionTicketEventEntity::getTicketId, ticketIds)
                        .eq(ExceptionTicketEventEntity::getAction, "ESCALATE"))
                .stream()
                .map(ExceptionTicketEventEntity::getTicketId)
                .collect(Collectors.toSet());
    }

    private void notifyTicketAction(
            ExceptionTicketEntity ticket,
            String action,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        String notificationType = switch (action) {
            case "START" -> "EXCEPTION_TICKET_STARTED";
            case "RESOLVE" -> "EXCEPTION_TICKET_RESOLVED";
            case "CLOSE" -> "EXCEPTION_TICKET_CLOSED";
            default -> null;
        };
        if (notificationType == null) {
            return;
        }
        notifyTicket(
                "NOTICE",
                notificationType,
                ticketActionTitle(action, ticket),
                comment,
                ticket,
                ticketRecipients(ticket),
                audit,
                now
        );
    }

    private void notifyTicket(
            String category,
            String notificationType,
            String title,
            String content,
            ExceptionTicketEntity ticket,
            List<Long> recipientUserIds,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        notificationService.createBusinessNotification(
                category,
                notificationType,
                title,
                content,
                NOTIFICATION_BUSINESS_TYPE,
                ticket.getId(),
                ticket.getTicketNo(),
                ticketTargetUrl(ticket),
                recipientUserIds,
                audit,
                now
        );
    }

    private String ticketActionTitle(String action, ExceptionTicketEntity ticket) {
        return switch (action) {
            case "START" -> "异常工单开始处理：" + ticket.getTicketNo();
            case "RESOLVE" -> "异常工单已解决：" + ticket.getTicketNo();
            case "CLOSE" -> "异常工单已关闭：" + ticket.getTicketNo();
            default -> "异常工单状态更新：" + ticket.getTicketNo();
        };
    }

    private List<Long> ticketRecipients(ExceptionTicketEntity ticket) {
        List<Long> recipients = new ArrayList<>();
        recipients.add(ticket.getAssigneeUserId());
        recipients.add(ticket.getCreatedBy());
        return recipients(recipients);
    }

    private List<Long> recipients(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return recipients(List.of(userId));
    }

    private List<Long> recipients(List<Long> userIds) {
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                recipients.add(userId);
            }
        }
        return List.copyOf(recipients);
    }

    private String ticketTargetUrl(ExceptionTicketEntity ticket) {
        return "/exception-tickets?keyword=" + ticket.getTicketNo();
    }

    private AuditMetadata systemAudit(ExceptionTicketEntity ticket, LocalDateTime now) {
        return new AuditMetadata(SYSTEM_USER_ID, ticket.getCompanyId(), ticket.getAccountBookId(), now);
    }

    private ExceptionTicketResponse toResponse(ExceptionTicketEntity ticket, List<ExceptionTicketEventEntity> events) {
        return new ExceptionTicketResponse(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getSourceType(),
                ticket.getSourceId(),
                ticket.getSourceNo(),
                ticket.getSourceRoute(),
                isTraceable(ticket),
                traceKeyword(ticket),
                traceRoute(ticket),
                ticket.getStatus(),
                ticket.getAssigneeUserId(),
                ticket.getDueTime(),
                ticket.getResolvedBy(),
                ticket.getResolvedTime(),
                ticket.getResolution(),
                ticket.getCreatedBy(),
                ticket.getCreatedTime(),
                ticket.getUpdatedTime(),
                events.stream().map(this::toEventResponse).toList()
        );
    }

    private boolean isTraceable(ExceptionTicketEntity ticket) {
        return traceKeyword(ticket) != null;
    }

    private String traceKeyword(ExceptionTicketEntity ticket) {
        return trimToNull(ticket.getSourceNo());
    }

    private String traceRoute(ExceptionTicketEntity ticket) {
        String keyword = traceKeyword(ticket);
        if (keyword == null) {
            return null;
        }
        return "/reports/traces?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private ExceptionTicketEventResponse toEventResponse(ExceptionTicketEventEntity event) {
        return new ExceptionTicketEventResponse(
                event.getId(),
                event.getTicketId(),
                event.getAction(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getComment(),
                event.getOperatorUserId(),
                event.getCreatedTime()
        );
    }

    private void touch(ExceptionTicketEntity ticket, AuditMetadata audit, LocalDateTime now) {
        ticket.setUpdatedBy(audit.userId());
        ticket.setUpdatedTime(now);
    }

    private void rejectClosed(ExceptionTicketEntity ticket) {
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new IllegalArgumentException("已关闭的异常工单不能修改");
        }
    }

    private String nextTicketNo(AuditMetadata audit) {
        String bizDate = LocalDate.now(clock).format(TICKET_DATE_FORMATTER);
        long persistedValue = latestTicketSequence(audit, bizDate);
        long nextValue = ticketNoCounter.updateAndGet(value -> {
            long baseline = Math.max(value, persistedValue);
            return baseline >= 9999L ? 1L : baseline + 1L;
        });
        return "ET-" + bizDate + "-" + String.format("%04d", nextValue);
    }

    private long latestTicketSequence(AuditMetadata audit, String bizDate) {
        String prefix = "ET-" + bizDate + "-";
        ExceptionTicketEntity latest = ticketMapper.selectOne(new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, audit.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, audit.accountBookId())
                .eq(ExceptionTicketEntity::getDeletedFlag, 0)
                .likeRight(ExceptionTicketEntity::getTicketNo, prefix)
                .orderByDesc(ExceptionTicketEntity::getTicketNo)
                .last("LIMIT 1"));
        return parseTicketSequence(latest == null ? null : latest.getTicketNo(), prefix);
    }

    private long parseTicketSequence(String ticketNo, String prefix) {
        if (ticketNo == null || !ticketNo.startsWith(prefix)) {
            return 0L;
        }
        try {
            return Long.parseLong(ticketNo.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeCodeOrDefault(String value, String defaultValue) {
        String normalized = normalizeCode(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String commentOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
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
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
