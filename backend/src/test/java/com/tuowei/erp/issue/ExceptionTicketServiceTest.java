package com.tuowei.erp.issue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEventEntity;
import com.tuowei.erp.issue.sla.service.ExceptionSlaEscalationPolicy;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.service.ExceptionTicketQueryService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionTicketServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );

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
    void createsTicketWithAuditFieldsAndCreateEvent() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionTicketCreateRequest request = createRequest();

        var response = service().create(request);

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.ticketNo()).startsWith("ET-20260630-");
        assertThat(response.title()).isEqualTo("库存低于安全线");

        ArgumentCaptor<ExceptionTicketEntity> ticketCaptor = ArgumentCaptor.forClass(ExceptionTicketEntity.class);
        verify(ticketMapper).insert(ticketCaptor.capture());
        ExceptionTicketEntity ticket = ticketCaptor.getValue();
        assertThat(ticket.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(ticket.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(ticket.getStatus()).isEqualTo("OPEN");
        assertThat(ticket.getCreatedBy()).isEqualTo(AUDIT.userId());

        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor = ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("CREATE");
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo("OPEN");

        verify(notificationService).createBusinessNotification(
                eq("TODO"),
                eq("EXCEPTION_TICKET_CREATED"),
                any(),
                any(),
                eq("EXCEPTION_TICKET"),
                any(),
                any(),
                any(),
                eq(List.of(9002L)),
                eq(AUDIT),
                eq(AUDIT.now())
        );
    }

    @Test
    void createsTicketNumberAfterExistingSameDayTicket() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionTicketEntity existing = openTicket();
        existing.setTicketNo("ET-20260630-0042");
        lenient().when(ticketMapper.selectOne(any())).thenReturn(existing);

        var response = service().create(createRequest());

        assertThat(response.ticketNo()).isEqualTo("ET-20260630-0043");
    }

    @Test
    void listsTicketsWithTenantScopedFiltersAndEvents() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionTicketPageQuery query = new ExceptionTicketPageQuery();
        query.setKeyword("库存");
        query.setStatus("OPEN");
        query.setPriority("HIGH");
        query.setCategory("LOW_STOCK");
        query.setAssigneeUserId(9002L);
        query.setSourceNo("SO-001");
        query.setOverdueOnly(true);
        query.setPageNo(1);
        query.setPageSize(20);
        when(ticketMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExceptionTicketEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(openTicket()));
            return page;
        });
        when(eventMapper.selectList(any())).thenReturn(List.of(event(2001L, "CREATE", null, "OPEN")));

        var response = service().list(query);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().get(0).events()).hasSize(1);
        assertThat(response.records().get(0).traceable()).isTrue();
        assertThat(response.records().get(0).traceKeyword()).isEqualTo("SO-001");
        assertThat(response.records().get(0).traceRoute()).isEqualTo("/reports/traces?keyword=SO-001");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("status")
                .contains("priority")
                .contains("category")
                .contains("assignee_user_id")
                .contains("source_no")
                .contains("due_time");
    }

    @Test
    void stateTransitionsUpdateTicketAndWriteEvents() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ticketMapper.selectOne(any())).thenReturn(openTicket(), processingTicket(), resolvedTicket(), resolvedTicket());

        var assigned = service().assign(1001L, new ExceptionTicketAssignRequest(9003L, "转仓库主管"));
        var started = service().start(1001L, new ExceptionTicketActionRequest("开始处理"));
        var resolved = service().resolve(1001L, new ExceptionTicketActionRequest("已补货"));
        var closed = service().close(1001L, new ExceptionTicketActionRequest("确认关闭"));

        assertThat(assigned.assigneeUserId()).isEqualTo(9003L);
        assertThat(started.status()).isEqualTo("PROCESSING");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(closed.status()).isEqualTo("CLOSED");

        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor = ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper, org.mockito.Mockito.times(4)).insert(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(ExceptionTicketEventEntity::getAction)
                .containsExactly("ASSIGN", "START", "RESOLVE", "CLOSE");
        verify(notificationService, times(4)).createBusinessNotification(
                any(),
                any(),
                any(),
                any(),
                eq("EXCEPTION_TICKET"),
                eq(1001L),
                eq("ET-20260630-0001"),
                any(),
                anyList(),
                eq(AUDIT),
                eq(AUDIT.now())
        );
    }

    @Test
    void escalatesOverdueTicketsOnceAndNotifiesRecipients() {
        ExceptionTicketEntity overdue = openTicket();
        overdue.setPriority("MEDIUM");
        overdue.setDueTime(AUDIT.now().minusMinutes(1));
        when(ticketMapper.selectList(any())).thenReturn(List.of(overdue));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(slaPolicyService.resolveEscalation(any(ExceptionTicketEntity.class), any(AuditMetadata.class)))
                .thenReturn(new ExceptionSlaEscalationPolicy(true, "URGENT"));

        int count = service().escalateOverdueTickets(AUDIT.now());

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<ExceptionTicketEntity> ticketCaptor = ArgumentCaptor.forClass(ExceptionTicketEntity.class);
        verify(ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getPriority()).isEqualTo("URGENT");
        assertThat(ticketCaptor.getValue().getUpdatedBy()).isZero();

        ArgumentCaptor<ExceptionTicketEventEntity> eventCaptor = ArgumentCaptor.forClass(ExceptionTicketEventEntity.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("ESCALATE");
        assertThat(eventCaptor.getValue().getOperatorUserId()).isZero();

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
                any(AuditMetadata.class),
                eq(AUDIT.now())
        );
    }

    @Test
    void skipsOverdueTicketsWhenSlaPolicyDisablesEscalation() {
        ExceptionTicketEntity overdue = openTicket();
        overdue.setPriority("HIGH");
        overdue.setDueTime(AUDIT.now().minusMinutes(1));
        when(ticketMapper.selectList(any())).thenReturn(List.of(overdue));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(slaPolicyService.resolveEscalation(any(ExceptionTicketEntity.class), any(AuditMetadata.class)))
                .thenReturn(new ExceptionSlaEscalationPolicy(false, "HIGH"));

        int count = service().escalateOverdueTickets(AUDIT.now());

        assertThat(count).isZero();
        verify(ticketMapper, never()).updateById(any(ExceptionTicketEntity.class));
        verify(notificationService, never()).createBusinessNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsAlreadyEscalatedOverdueTickets() {
        ExceptionTicketEntity overdue = openTicket();
        overdue.setDueTime(AUDIT.now().minusMinutes(1));
        when(ticketMapper.selectList(any())).thenReturn(List.of(overdue));
        when(eventMapper.selectList(any())).thenReturn(List.of(event(2002L, "ESCALATE", "OPEN", "OPEN")));

        int count = service().escalateOverdueTickets(AUDIT.now());

        assertThat(count).isZero();
        verify(ticketMapper, never()).updateById(any(ExceptionTicketEntity.class));
        verify(notificationService, never()).createBusinessNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsClosingOpenTicket() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ticketMapper.selectOne(any())).thenReturn(openTicket());

        assertThatThrownBy(() -> service().close(1001L, new ExceptionTicketActionRequest("不能关闭")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有已解决的异常工单可以关闭");
    }

    private ExceptionTicketService service() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ExceptionTicketQueryService queryService = new ExceptionTicketQueryService(
                auditMetadataFactory,
                ticketMapper,
                eventMapper,
                clock
        );
        return new ExceptionTicketService(
                auditMetadataFactory,
                ticketMapper,
                eventMapper,
                notificationService,
                slaPolicyService,
                queryService,
                clock
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
        request.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        return request;
    }

    private static ExceptionTicketEntity openTicket() {
        return ticket("OPEN", 9002L);
    }

    private static ExceptionTicketEntity processingTicket() {
        return ticket("PROCESSING", 9002L);
    }

    private static ExceptionTicketEntity resolvedTicket() {
        ExceptionTicketEntity entity = ticket("RESOLVED", 9002L);
        entity.setResolvedTime(LocalDateTime.of(2026, 6, 30, 11, 0));
        entity.setResolution("已补货");
        return entity;
    }

    private static ExceptionTicketEntity ticket(String status, Long assigneeUserId) {
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
        entity.setAssigneeUserId(assigneeUserId);
        entity.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(AUDIT.userId());
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 30, 10, 0));
        entity.setUpdatedBy(AUDIT.userId());
        entity.setUpdatedTime(LocalDateTime.of(2026, 6, 30, 10, 0));
        entity.setVersion(0);
        return entity;
    }

    private static ExceptionTicketEventEntity event(Long id, String action, String fromStatus, String toStatus) {
        ExceptionTicketEventEntity entity = new ExceptionTicketEventEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setTicketId(1001L);
        entity.setAction(action);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setComment("事件");
        entity.setOperatorUserId(AUDIT.userId());
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 30, 10, 0));
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
