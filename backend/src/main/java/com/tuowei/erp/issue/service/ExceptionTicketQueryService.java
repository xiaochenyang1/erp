package com.tuowei.erp.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEventEntity;
import com.tuowei.erp.issue.web.ExceptionTicketEventResponse;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Read-side filtering, tenant isolation, event hydration and response mapping for exception tickets. */
@Service
public class ExceptionTicketQueryService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private final AuditMetadataFactory auditMetadataFactory;
    private final ExceptionTicketMapper ticketMapper;
    private final ExceptionTicketEventMapper eventMapper;
    private final Clock clock;

    public ExceptionTicketQueryService(
            AuditMetadataFactory auditMetadataFactory,
            ExceptionTicketMapper ticketMapper,
            ExceptionTicketEventMapper eventMapper,
            Clock clock
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionTicketResponse> list(ExceptionTicketPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExceptionTicketPageQuery safeQuery = query == null ? new ExceptionTicketPageQuery() : query;
        Page<ExceptionTicketEntity> result = ticketMapper.selectPage(
                new Page<>(
                        PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo()),
                        PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize())
                ),
                buildTicketQuery(audit, safeQuery)
        );
        Map<Long, List<ExceptionTicketEventEntity>> events = loadEvents(result.getRecords(), audit);
        List<ExceptionTicketResponse> records = result.getRecords().stream()
                .map(ticket -> toResponse(ticket, events.getOrDefault(ticket.getId(), List.of())))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Transactional(readOnly = true)
    public ExceptionTicketEntity requireTicket(Long id, AuditMetadata audit) {
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

    public ExceptionTicketResponse toResponse(
            ExceptionTicketEntity ticket,
            List<ExceptionTicketEventEntity> events
    ) {
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

    private LambdaQueryWrapper<ExceptionTicketEntity> buildTicketQuery(
            AuditMetadata audit,
            ExceptionTicketPageQuery query
    ) {
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
        return wrapper.orderByDesc(ExceptionTicketEntity::getUpdatedTime)
                .orderByDesc(ExceptionTicketEntity::getId);
    }

    private void eqIfText(
            LambdaQueryWrapper<ExceptionTicketEntity> wrapper,
            SFunction<ExceptionTicketEntity, ?> column,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            wrapper.eq(column, value);
        }
    }

    private Map<Long, List<ExceptionTicketEventEntity>> loadEvents(
            List<ExceptionTicketEntity> tickets,
            AuditMetadata audit
    ) {
        List<Long> ticketIds = tickets.stream()
                .map(ExceptionTicketEntity::getId)
                .filter(Objects::nonNull)
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

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
