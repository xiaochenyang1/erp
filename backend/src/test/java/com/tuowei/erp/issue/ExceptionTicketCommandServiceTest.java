package com.tuowei.erp.issue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEventEntity;
import com.tuowei.erp.issue.service.ExceptionTicketCommandService;
import com.tuowei.erp.issue.service.ExceptionTicketQueryService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaEscalationPolicy;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.system.notification.service.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionTicketCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-30T02:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );
    private static final LocalDateTime CLOCK_NOW = LocalDateTime.of(2026, 6, 30, 10, 0);

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private ExceptionTicketMapper ticketMapper;

    @Mock
    private ExceptionTicketEventMapper eventMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ExceptionSlaPolicyService slaPolicyService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExceptionTicketEntity.class);
        initTableInfo(ExceptionTicketEventEntity.class);
    }

    @Test
    void repeatedStartKeepsProcessingStatusButWritesEventAndNotification() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionTicketEntity ticket = ticket("PROCESSING");
        when(ticketMapper.selectOne(any())).thenReturn(ticket);

        var response = commandService().start(1001L, new ExceptionTicketActionRequest("继续处理"));

        assertThat(response.status()).isEqualTo("PROCESSING");
        verify(ticketMapper).updateById(ticket);
        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor =
                ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("START");
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo("PROCESSING");
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo("PROCESSING");
        assertThat(eventCaptor.getValue().getComment()).isEqualTo("继续处理");
        verify(notificationService).createBusinessNotification(
                eq("NOTICE"),
                eq("EXCEPTION_TICKET_STARTED"),
                any(),
                eq("继续处理"),
                eq("EXCEPTION_TICKET"),
                eq(1001L),
                eq("ET-20260630-0001"),
                any(),
                eq(List.of(9002L, 9001L)),
                eq(AUDIT),
                eq(AUDIT.now())
        );
    }

    @Test
    void repeatedResolvePreservesOriginalResolutionMetadataButWritesEventAndNotification() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionTicketEntity ticket = ticket("RESOLVED");
        Long originalResolvedBy = 7777L;
        LocalDateTime originalResolvedTime = LocalDateTime.of(2026, 6, 29, 17, 30);
        ticket.setResolution("原始解决方案");
        ticket.setResolvedBy(originalResolvedBy);
        ticket.setResolvedTime(originalResolvedTime);
        when(ticketMapper.selectOne(any())).thenReturn(ticket);

        var response = commandService().resolve(1001L, new ExceptionTicketActionRequest("重复解决说明"));

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolution()).isEqualTo("原始解决方案");
        assertThat(response.resolvedBy()).isEqualTo(originalResolvedBy);
        assertThat(response.resolvedTime()).isEqualTo(originalResolvedTime);
        verify(ticketMapper).updateById(ticket);
        assertThat(ticket.getResolution()).isEqualTo("原始解决方案");
        assertThat(ticket.getResolvedBy()).isEqualTo(originalResolvedBy);
        assertThat(ticket.getResolvedTime()).isEqualTo(originalResolvedTime);

        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor =
                ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("RESOLVE");
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo("RESOLVED");
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo("RESOLVED");
        assertThat(eventCaptor.getValue().getComment()).isEqualTo("重复解决说明");
        verify(notificationService).createBusinessNotification(
                eq("NOTICE"),
                eq("EXCEPTION_TICKET_RESOLVED"),
                any(),
                eq("重复解决说明"),
                eq("EXCEPTION_TICKET"),
                eq(1001L),
                eq("ET-20260630-0001"),
                any(),
                eq(List.of(9002L, 9001L)),
                eq(AUDIT),
                eq(AUDIT.now())
        );
    }

    @Test
    void assignClosedTicketRejectsBeforeAnyWrite() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ticketMapper.selectOne(any())).thenReturn(ticket("CLOSED"));

        assertThatThrownBy(() -> commandService().assign(
                1001L,
                new ExceptionTicketAssignRequest(9010L, "重新分派")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已关闭的异常工单不能修改");

        verify(ticketMapper, never()).updateById(any(ExceptionTicketEntity.class));
        verify(eventMapper, never()).insert(any(ExceptionTicketEventEntity.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void createWithExplicitAuditDoesNotReadAuditFactoryAndRetainsDueTime() {
        ExceptionTicketCreateRequest request = createRequest();
        LocalDateTime dueTime = LocalDateTime.of(2026, 7, 1, 9, 15);
        request.setDueTime(dueTime);
        when(ticketMapper.selectOne(any())).thenReturn(null);

        var response = commandService().create(request, AUDIT);

        verifyNoInteractions(auditMetadataFactory);
        ArgumentCaptor<ExceptionTicketEntity> ticketCaptor =
                ArgumentCaptor.forClass(ExceptionTicketEntity.class);
        verify(ticketMapper).insert(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getDueTime()).isEqualTo(dueTime);
        assertThat(response.dueTime()).isEqualTo(dueTime);
    }

    @Test
    void nullEscalationTimeUsesInjectedClock() {
        ExceptionTicketEntity overdue = ticket("OPEN");
        overdue.setPriority("MEDIUM");
        overdue.setDueTime(CLOCK_NOW.minusMinutes(1));
        when(ticketMapper.selectList(any())).thenReturn(List.of(overdue));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(slaPolicyService.resolveEscalation(any(ExceptionTicketEntity.class), any(AuditMetadata.class)))
                .thenReturn(new ExceptionSlaEscalationPolicy(true, "HIGH"));

        int count = commandService().escalateOverdueTickets(null);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<ExceptionTicketEntity> ticketCaptor =
                ArgumentCaptor.forClass(ExceptionTicketEntity.class);
        verify(ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getUpdatedTime()).isEqualTo(CLOCK_NOW);
        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor =
                ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getCreatedTime()).isEqualTo(CLOCK_NOW);
        verify(notificationService).createBusinessNotification(
                eq("TODO"),
                eq("EXCEPTION_TICKET_ESCALATED"),
                any(),
                any(),
                eq("EXCEPTION_TICKET"),
                eq(1001L),
                eq("ET-20260630-0001"),
                any(),
                eq(List.of(9002L, 9001L)),
                eq(new AuditMetadata(0L, AUDIT.companyId(), AUDIT.accountBookId(), CLOCK_NOW)),
                eq(CLOCK_NOW)
        );
    }

    private ExceptionTicketCommandService commandService() {
        ExceptionTicketQueryService queryService = new ExceptionTicketQueryService(
                auditMetadataFactory,
                ticketMapper,
                eventMapper,
                CLOCK
        );
        return new ExceptionTicketCommandService(
                auditMetadataFactory,
                ticketMapper,
                eventMapper,
                notificationService,
                slaPolicyService,
                queryService,
                CLOCK
        );
    }

    private static ExceptionTicketCreateRequest createRequest() {
        ExceptionTicketCreateRequest request = new ExceptionTicketCreateRequest();
        request.setCategory("LOW_STOCK");
        request.setPriority("HIGH");
        request.setTitle("库存低于安全线");
        request.setDescription("A 仓原材料库存不足");
        request.setSourceType("LOW_STOCK");
        request.setSourceId(7001L);
        request.setSourceNo("SO-001");
        request.setSourceRoute("/inventory/alerts");
        request.setAssigneeUserId(9002L);
        return request;
    }

    private static ExceptionTicketEntity ticket(String status) {
        ExceptionTicketEntity entity = new ExceptionTicketEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setTicketNo("ET-20260630-0001");
        entity.setCategory("LOW_STOCK");
        entity.setPriority("HIGH");
        entity.setTitle("库存低于安全线");
        entity.setDescription("A 仓原材料库存不足");
        entity.setSourceType("LOW_STOCK");
        entity.setSourceId(7001L);
        entity.setSourceNo("SO-001");
        entity.setSourceRoute("/inventory/alerts");
        entity.setStatus(status);
        entity.setAssigneeUserId(9002L);
        entity.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(AUDIT.userId());
        entity.setCreatedTime(AUDIT.now());
        entity.setUpdatedBy(AUDIT.userId());
        entity.setUpdatedTime(AUDIT.now());
        entity.setVersion(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
