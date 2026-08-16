package com.tuowei.erp.issue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEventEntity;
import com.tuowei.erp.issue.service.ExceptionTicketQueryService;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExceptionTicketQueryServiceTest {

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

    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final ExceptionTicketMapper ticketMapper = mock(ExceptionTicketMapper.class);
    private final ExceptionTicketEventMapper eventMapper = mock(ExceptionTicketEventMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExceptionTicketEntity.class);
        initTableInfo(ExceptionTicketEventEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationHydratesTenantEventsAndMapsTrace() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ticketMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ExceptionTicketEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(ticket("SO A/001")));
            return page;
        });
        when(eventMapper.selectList(any())).thenReturn(List.of(event()));
        ExceptionTicketPageQuery query = fullQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.ticketNo()).isEqualTo("ET-20260630-0001");
            assertThat(response.traceable()).isTrue();
            assertThat(response.traceKeyword()).isEqualTo("SO A/001");
            assertThat(response.traceRoute()).isEqualTo("/reports/traces?keyword=SO+A%2F001");
            assertThat(response.events()).singleElement().satisfies(mappedEvent -> {
                assertThat(mappedEvent.id()).isEqualTo(2001L);
                assertThat(mappedEvent.action()).isEqualTo("CREATE");
                assertThat(mappedEvent.toStatus()).isEqualTo("OPEN");
            });
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<ExceptionTicketEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEntity>> ticketQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectPage(pageCaptor.capture(), ticketQueryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertNormalizedTicketQuery(ticketQueryCaptor.getValue());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEventEntity>> eventQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(eventMapper).selectList(eventQueryCaptor.capture());
        assertThat(eventQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "ticket_id", "created_time");
        assertThat(eventQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 1001L);
    }

    @Test
    void listUsesDefaultPaginationAndSkipsEventQueryForEmptyPage() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ticketMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ExceptionTicketEntity> page = invocation.getArgument(0);
            page.setTotal(0L);
            page.setRecords(List.of());
            return page;
        });

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        verify(eventMapper, never()).selectList(any());
    }

    @Test
    void requireTicketUsesTenantScopedLookup() {
        ExceptionTicketEntity ticket = ticket("SO-001");
        when(ticketMapper.selectOne(any())).thenReturn(ticket);

        var result = service().requireTicket(ticket.getId(), AUDIT);

        assertThat(result).isSameAs(ticket);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "id");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), ticket.getId());
    }

    @Test
    void requireTicketRejectsMissingTenantRecord() {
        when(ticketMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().requireTicket(1001L, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("异常工单不存在");
    }

    @Test
    void toResponseMarksBlankSourceNumberAsNotTraceable() {
        ExceptionTicketEntity ticket = ticket("  ");

        var result = service().toResponse(ticket, List.of());

        assertThat(result.traceable()).isFalse();
        assertThat(result.traceKeyword()).isNull();
        assertThat(result.traceRoute()).isNull();
    }

    private ExceptionTicketQueryService service() {
        return new ExceptionTicketQueryService(
                auditMetadataFactory,
                ticketMapper,
                eventMapper,
                CLOCK
        );
    }

    private ExceptionTicketPageQuery fullQuery() {
        ExceptionTicketPageQuery query = new ExceptionTicketPageQuery();
        query.setKeyword("  库存  ");
        query.setStatus("  open  ");
        query.setPriority("  high  ");
        query.setCategory("  low_stock  ");
        query.setAssigneeUserId(9002L);
        query.setSourceNo("  SO-001  ");
        query.setOverdueOnly(true);
        return query;
    }

    private void assertNormalizedTicketQuery(LambdaQueryWrapper<ExceptionTicketEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "company_id",
                        "account_book_id",
                        "deleted_flag",
                        "ticket_no",
                        "title",
                        "description",
                        "status",
                        "priority",
                        "category",
                        "assignee_user_id",
                        "source_no",
                        "due_time",
                        "updated_time"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).contains(
                AUDIT.companyId(),
                AUDIT.accountBookId(),
                "%库存%",
                "OPEN",
                "HIGH",
                "LOW_STOCK",
                9002L,
                "%SO-001%",
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }

    private ExceptionTicketEntity ticket(String sourceNo) {
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
        entity.setSourceNo(sourceNo);
        entity.setSourceRoute("/inventory/alerts");
        entity.setStatus("OPEN");
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

    private ExceptionTicketEventEntity event() {
        ExceptionTicketEventEntity entity = new ExceptionTicketEventEntity();
        entity.setId(2001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setTicketId(1001L);
        entity.setAction("CREATE");
        entity.setToStatus("OPEN");
        entity.setComment("创建异常工单");
        entity.setOperatorUserId(AUDIT.userId());
        entity.setCreatedTime(AUDIT.now());
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
