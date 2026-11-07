package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import com.tuowei.erp.inventory.stock.service.InventoryReservationQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationQueryServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9401L,
            101L,
            202L,
            11L,
            12L,
            "reservation_query_user",
            "预占查询用户"
    );
    private static final DataScopeSnapshot SNAPSHOT = DataScopeSnapshot.all();
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            USER.userId(),
            USER.companyId(),
            USER.accountBookId(),
            USER.deptId(),
            USER.postId(),
            USER.username(),
            USER.realName(),
            "N/A",
            Set.of(),
            SNAPSHOT
    );

    @Mock
    private InventoryReservationMapper reservationMapper;

    @Mock
    private InventoryReservationEventMapper reservationEventMapper;

    @Mock
    private InventoryBalanceMapper balanceMapper;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryReservationEntity.class);
        initTableInfo(InventoryReservationEventEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
    }

    @Test
    void listReservationsNormalizesFiltersCapsPaginationAndMapsQuantities() {
        stubCurrentUser();
        stubReservationScope();
        when(reservationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryReservationEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(reservation(9001L)));
            return page;
        });
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59);
        InventoryReservationPageQuery query = new InventoryReservationPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setWarehouseId(3001L);
        query.setProductId(4001L);
        query.setSourceType(" sales_order ");
        query.setSourceNo(" SO-7001 ");
        query.setStatus(" active ");
        query.setCreatedTimeFrom(from);
        query.setCreatedTimeTo(to);

        var response = service().listReservations(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<InventoryReservationEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        assertTenantScoped(wrapperCaptor.getValue());
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(3001L, 4001L, "SALES_ORDER", "%SO-7001%", "ACTIVE", from, to);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.reservedQty()).isEqualByComparingTo("5.0000");
            assertThat(record.releasedQty()).isEqualByComparingTo("1.0000");
            assertThat(record.remainingQty()).isEqualByComparingTo("4.0000");
        });
    }

    @Test
    void summaryAggregatesReservationsAndMapsBalanceAvailability() {
        stubCurrentUser();
        stubReservationScope();
        stubBalanceScope();
        InventoryReservationEntity first = reservation(9001L);
        InventoryReservationEntity second = reservation(9002L);
        second.setReservedQty(new BigDecimal("3.0000"));
        second.setReleasedQty(BigDecimal.ZERO);
        second.setRemainingQty(new BigDecimal("3.0000"));
        when(reservationMapper.selectList(any())).thenReturn(List.of(first, second));
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setWarehouseId(3001L);
        balance.setProductId(4001L);
        balance.setQtyOnHand(new BigDecimal("10.0000"));
        balance.setQtyReserved(new BigDecimal("7.0000"));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance));

        var response = service().summary(new InventoryReservationSummaryQuery());

        assertThat(response).singleElement().satisfies(summary -> {
            assertThat(summary.reservedQty()).isEqualByComparingTo("8.0000");
            assertThat(summary.releasedQty()).isEqualByComparingTo("1.0000");
            assertThat(summary.remainingQty()).isEqualByComparingTo("7.0000");
            assertThat(summary.qtyOnHand()).isEqualByComparingTo("10.0000");
            assertThat(summary.qtyReserved()).isEqualByComparingTo("7.0000");
            assertThat(summary.qtyAvailable()).isEqualByComparingTo("3.0000");
            assertThat(summary.reservationCount()).isEqualTo(2);
        });
    }

    @Test
    void sourceRejectsEmptyCriteriaBeforeLoadingReservations() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);

        assertThatThrownBy(() -> service().source(new InventoryReservationSourceQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("预占来源查询条件不能为空");
        verifyNoInteractions(reservationMapper, reservationEventMapper, balanceMapper, dataScopeService);
    }

    @Test
    void sourceLoadsEventsForAllReservationsInOneBatch() {
        stubCurrentUser();
        stubReservationScope();
        InventoryReservationEntity first = reservation(9001L);
        InventoryReservationEntity second = reservation(9002L);
        when(reservationMapper.selectList(any())).thenReturn(List.of(first, second));
        InventoryReservationEventEntity firstEvent = event(9101L, first.getId(), "RESERVE");
        InventoryReservationEventEntity secondEvent = event(9102L, second.getId(), "RELEASE");
        when(reservationEventMapper.selectList(any())).thenReturn(List.of(firstEvent, secondEvent));
        InventoryReservationSourceQuery query = new InventoryReservationSourceQuery();
        query.setSourceType(" sales_order ");
        query.setSourceId(7001L);

        var response = service().source(query);

        assertThat(response.reservations()).hasSize(2);
        assertThat(response.reservations().get(0).events()).singleElement()
                .satisfies(mapped -> assertThat(mapped.eventType()).isEqualTo("RESERVE"));
        assertThat(response.reservations().get(1).events()).singleElement()
                .satisfies(mapped -> assertThat(mapped.eventType()).isEqualTo("RELEASE"));
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEventEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationEventMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("reservation_id")
                .contains(" in ");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(first.getId(), second.getId());
    }

    @Test
    void detailScopesTenantAndMapsOrderedEvents() {
        stubCurrentUser();
        when(reservationMapper.selectById(9001L)).thenReturn(reservation(9001L));
        InventoryReservationEventEntity event = new InventoryReservationEventEntity();
        event.setId(9101L);
        event.setReservationId(9001L);
        event.setEventType("RELEASE");
        event.setEventQty(new BigDecimal("1"));
        event.setRemainingQtyBefore(new BigDecimal("5"));
        event.setRemainingQtyAfter(new BigDecimal("4"));
        event.setCreatedBy(USER.userId());
        event.setCreatedTime(LocalDateTime.of(2026, 8, 14, 10, 0));
        when(reservationEventMapper.selectList(any())).thenReturn(List.of(event));

        var response = service().getReservation(9001L);

        assertThat(response.reservation().sourceNo()).isEqualTo("SO-7001");
        assertThat(response.events()).singleElement().satisfies(mapped -> {
            assertThat(mapped.eventQty()).isEqualByComparingTo("1.0000");
            assertThat(mapped.remainingQtyBefore()).isEqualByComparingTo("5.0000");
            assertThat(mapped.remainingQtyAfter()).isEqualByComparingTo("4.0000");
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEventEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationEventMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    private InventoryReservationQueryService service() {
        return new InventoryReservationQueryService(
                reservationMapper,
                reservationEventMapper,
                balanceMapper,
                currentUserContext,
                dataScopeService
        );
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubReservationScope() {
        when(dataScopeService.applyInventoryReservationScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubBalanceScope() {
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private InventoryReservationEntity reservation(Long id) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(id);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(7001L);
        entity.setSourceNo("SO-7001");
        entity.setSourceLineId(8001L);
        entity.setReservedQty(new BigDecimal("5.0000"));
        entity.setReleasedQty(new BigDecimal("1.0000"));
        entity.setRemainingQty(new BigDecimal("4.0000"));
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(LocalDateTime.of(2026, 8, 14, 8, 0));
        entity.setUpdatedTime(LocalDateTime.of(2026, 8, 14, 9, 0));
        return entity;
    }

    private InventoryReservationEventEntity event(Long id, Long reservationId, String eventType) {
        InventoryReservationEventEntity entity = new InventoryReservationEventEntity();
        entity.setId(id);
        entity.setReservationId(reservationId);
        entity.setEventType(eventType);
        entity.setEventQty(BigDecimal.ONE);
        entity.setRemainingQtyBefore(new BigDecimal("5"));
        entity.setRemainingQtyAfter(new BigDecimal("4"));
        entity.setCreatedBy(USER.userId());
        entity.setCreatedTime(LocalDateTime.of(2026, 8, 14, 10, 0));
        return entity;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
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
