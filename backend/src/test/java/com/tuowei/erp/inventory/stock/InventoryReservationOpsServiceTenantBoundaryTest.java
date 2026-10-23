package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
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
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCheckService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationOpsService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationManualReleaseRequest;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationOpsServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9301L,
            101L,
            202L,
            11L,
            12L,
            "reservation_scope_user",
            "库存预占用户"
    );
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            CURRENT_USER.userId(),
            CURRENT_USER.companyId(),
            CURRENT_USER.accountBookId(),
            CURRENT_USER.deptId(),
            CURRENT_USER.postId(),
            CURRENT_USER.username(),
            CURRENT_USER.realName(),
            "N/A",
            Set.of(),
            DataScopeSnapshot.all()
    );

    @Mock
    private InventoryReservationMapper reservationMapper;

    @Mock
    private InventoryReservationEventMapper reservationEventMapper;

    @Mock
    private InventoryBalanceMapper balanceMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private SystemLogService systemLogService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryReservationEntity.class);
        initTableInfo(InventoryReservationEventEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void listReservationsScopesQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        stubReservationScopePassThrough();
        when(reservationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryReservationEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().listReservations(new InventoryReservationPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void summaryScopesReservationsAndBalancesByCompanyAndAccountBook() {
        stubCurrentUser();
        stubReservationScopePassThrough();
        stubBalanceScopePassThrough();
        when(reservationMapper.selectList(any())).thenReturn(List.of());
        when(balanceMapper.selectList(any())).thenReturn(List.of());

        service().summary(new InventoryReservationSummaryQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationMapper).selectList(reservationWrapperCaptor.capture());
        assertTenantScoped(reservationWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(balanceMapper).selectList(balanceWrapperCaptor.capture());
        assertTenantScoped(balanceWrapperCaptor.getValue());
    }

    @Test
    void checksScopeReservationsAndBalancesByCompanyAndAccountBook() {
        stubCurrentUser();
        stubReservationScopePassThrough();
        stubBalanceScopePassThrough();
        when(reservationMapper.selectList(any())).thenReturn(List.of());
        when(balanceMapper.selectList(any())).thenReturn(List.of());

        service().checks(new InventoryReservationCheckQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationMapper).selectList(reservationWrapperCaptor.capture());
        assertTenantScoped(reservationWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(balanceMapper).selectList(balanceWrapperCaptor.capture());
        assertTenantScoped(balanceWrapperCaptor.getValue());
    }

    @Test
    void checksTreatsSalesOrderSourceFromDifferentAccountBookAsMissing() {
        stubCurrentUser();
        stubReservationScopePassThrough();
        stubBalanceScopePassThrough();
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation(CURRENT_USER.accountBookId())));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance()));
        when(salesOrderMapper.selectById(7001L)).thenReturn(salesOrder(9999L));
        when(salesOrderLineMapper.selectById(8001L)).thenReturn(salesOrderLine(9999L));

        List<InventoryReservationCheckIssueResponse> issues = service().checks(new InventoryReservationCheckQuery());

        assertThat(issues)
                .extracting(InventoryReservationCheckIssueResponse::issueType)
                .contains("RESERVATION_SOURCE_MISSING");
    }

    @Test
    void sourceScopesReservationsAndEventsByCompanyAndAccountBook() {
        stubCurrentUser();
        stubReservationScopePassThrough();
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation(CURRENT_USER.accountBookId())));
        when(reservationEventMapper.selectList(any())).thenReturn(List.of());
        InventoryReservationSourceQuery query = new InventoryReservationSourceQuery();
        query.setSourceType("SALES_ORDER");
        query.setSourceId(7001L);

        service().source(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationMapper).selectList(reservationWrapperCaptor.capture());
        assertTenantScoped(reservationWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEventEntity>> eventWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(reservationEventMapper).selectList(eventWrapperCaptor.capture());
        assertTenantScoped(eventWrapperCaptor.getValue());
    }

    @Test
    void getReservationRejectsDifferentAccountBookWithinSameCompany() {
        stubCurrentUserOnly();
        when(reservationMapper.selectById(9001L)).thenReturn(reservation(9999L));

        assertThatThrownBy(() -> service().getReservation(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存预占不存在");
    }

    @Test
    void manualReleaseScopesDraftDeliveriesByCompanyAndAccountBook() {
        stubCurrentUser();
        when(reservationMapper.selectById(9001L)).thenReturn(reservation(CURRENT_USER.accountBookId()));
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                LocalDateTime.of(2026, 6, 8, 14, 30)
        ));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of(draftDelivery()));
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(draftDeliveryLine()));
        when(reservationEventMapper.selectList(any())).thenReturn(List.of());

        service().manualRelease(
                9001L,
                new InventoryReservationManualReleaseRequest(new BigDecimal("1.0000"), "release scope test")
        );

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> lineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(lineWrapperCaptor.capture());
        assertTenantScoped(lineWrapperCaptor.getValue());
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubCurrentUserOnly() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
    }

    private void stubReservationScopePassThrough() {
        when(dataScopeService.applyInventoryReservationScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubBalanceScopePassThrough() {
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private InventoryReservationEntity reservation(Long accountBookId) {
        InventoryReservationEntity reservation = new InventoryReservationEntity();
        reservation.setId(9001L);
        reservation.setCompanyId(CURRENT_USER.companyId());
        reservation.setAccountBookId(accountBookId);
        reservation.setWarehouseId(3001L);
        reservation.setProductId(4001L);
        reservation.setSourceType("SALES_ORDER");
        reservation.setSourceId(7001L);
        reservation.setSourceNo("SO-7001");
        reservation.setSourceLineId(8001L);
        reservation.setReservedQty(new BigDecimal("5.0000"));
        reservation.setReleasedQty(new BigDecimal("0.0000"));
        reservation.setRemainingQty(new BigDecimal("5.0000"));
        reservation.setStatus("ACTIVE");
        reservation.setCreatedTime(LocalDateTime.of(2026, 6, 8, 10, 0));
        reservation.setUpdatedTime(LocalDateTime.of(2026, 6, 8, 10, 0));
        return reservation;
    }

    private InventoryBalanceEntity balance() {
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setId(9101L);
        balance.setCompanyId(CURRENT_USER.companyId());
        balance.setAccountBookId(CURRENT_USER.accountBookId());
        balance.setWarehouseId(3001L);
        balance.setProductId(4001L);
        balance.setQtyOnHand(new BigDecimal("10.0000"));
        balance.setQtyReserved(new BigDecimal("5.0000"));
        return balance;
    }

    private SalesOrderEntity salesOrder(Long accountBookId) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(7001L);
        order.setCompanyId(CURRENT_USER.companyId());
        order.setAccountBookId(accountBookId);
        order.setOrderNo("SO-7001");
        order.setStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setDeliveryStatus("PARTIAL_DELIVERED");
        order.setDeletedFlag(0);
        return order;
    }

    private SalesOrderLineEntity salesOrderLine(Long accountBookId) {
        SalesOrderLineEntity line = new SalesOrderLineEntity();
        line.setId(8001L);
        line.setCompanyId(CURRENT_USER.companyId());
        line.setAccountBookId(accountBookId);
        line.setOrderId(7001L);
        line.setProductId(4001L);
        line.setQty(new BigDecimal("5.0000"));
        return line;
    }

    private SalesDeliveryEntity draftDelivery() {
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(7101L);
        delivery.setCompanyId(CURRENT_USER.companyId());
        delivery.setAccountBookId(CURRENT_USER.accountBookId());
        delivery.setOrderId(7001L);
        delivery.setStatus("DRAFT");
        delivery.setDeletedFlag(0);
        return delivery;
    }

    private SalesDeliveryLineEntity draftDeliveryLine() {
        SalesDeliveryLineEntity line = new SalesDeliveryLineEntity();
        line.setId(7201L);
        line.setCompanyId(CURRENT_USER.companyId());
        line.setAccountBookId(CURRENT_USER.accountBookId());
        line.setDeliveryId(7101L);
        line.setOrderLineId(8001L);
        line.setQty(new BigDecimal("1.0000"));
        return line;
    }

    private InventoryReservationOpsService service() {
        return new InventoryReservationOpsService(
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                inventoryPostingService,
                auditMetadataFactory,
                currentUserContext,
                systemLogService,
                new InventoryReservationQueryService(
                        reservationMapper,
                        reservationEventMapper,
                        balanceMapper,
                        currentUserContext,
                        dataScopeService
                ),
                new InventoryReservationCheckService(
                        reservationMapper,
                        balanceMapper,
                        salesOrderMapper,
                        salesOrderLineMapper,
                        currentUserContext,
                        dataScopeService
                )
        );
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
