package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCheckService;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationCheckServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9401L,
            101L,
            202L,
            11L,
            12L,
            "reservation_check_user",
            "预占检查用户"
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
    private InventoryBalanceMapper balanceMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryReservationEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @BeforeEach
    void stubScope() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(dataScopeService.applyInventoryReservationScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checksReturnsNoIssuesForConsistentApprovedSalesReservation() {
        InventoryReservationEntity reservation = reservation();
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(3001L, 4001L, "10", "4")));
        when(salesOrderMapper.selectById(7001L)).thenReturn(salesOrder(USER.accountBookId(), "PARTIAL_DELIVERED"));
        when(salesOrderLineMapper.selectById(8001L)).thenReturn(salesOrderLine(USER.accountBookId()));

        List<InventoryReservationCheckIssueResponse> issues = service().checks(new InventoryReservationCheckQuery());

        assertThat(issues).isEmpty();
    }

    @Test
    void checksReportsQuantityMissingBalanceMismatchAndNegativeAvailability() {
        InventoryReservationEntity reservation = reservation();
        reservation.setSourceType("PRODUCTION_ORDER");
        reservation.setReservedQty(new BigDecimal("5"));
        reservation.setReleasedQty(new BigDecimal("4"));
        reservation.setRemainingQty(new BigDecimal("3"));
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(3002L, 4002L, "1", "2")));

        List<InventoryReservationCheckIssueResponse> issues = service().checks(new InventoryReservationCheckQuery());

        assertThat(issues).extracting(InventoryReservationCheckIssueResponse::issueType)
                .containsExactlyInAnyOrder(
                        "RESERVATION_QUANTITY_INVALID",
                        "RESERVATION_BALANCE_MISSING",
                        "BALANCE_RESERVED_MISMATCH",
                        "BALANCE_AVAILABLE_NEGATIVE"
                );
    }

    @Test
    void checksTreatsCrossAccountBookSalesSourceAsMissing() {
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation()));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(3001L, 4001L, "10", "4")));
        when(salesOrderMapper.selectById(7001L)).thenReturn(salesOrder(9999L, "PARTIAL_DELIVERED"));
        when(salesOrderLineMapper.selectById(8001L)).thenReturn(salesOrderLine(9999L));

        List<InventoryReservationCheckIssueResponse> issues = service().checks(new InventoryReservationCheckQuery());

        assertThat(issues).extracting(InventoryReservationCheckIssueResponse::issueType)
                .containsExactly("RESERVATION_SOURCE_MISSING");
    }

    @Test
    void checksWarnsWhenApprovedSalesOrderIsFullyDeliveredWithRemainingReservation() {
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation()));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(3001L, 4001L, "10", "4")));
        when(salesOrderMapper.selectById(7001L)).thenReturn(salesOrder(USER.accountBookId(), "FULL_DELIVERED"));
        when(salesOrderLineMapper.selectById(8001L)).thenReturn(salesOrderLine(USER.accountBookId()));

        List<InventoryReservationCheckIssueResponse> issues = service().checks(new InventoryReservationCheckQuery());

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.issueType()).isEqualTo("RESERVATION_SOURCE_STATUS_INVALID");
            assertThat(issue.severity()).isEqualTo("WARN");
        });
    }

    private InventoryReservationCheckService service() {
        return new InventoryReservationCheckService(
                reservationMapper,
                balanceMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                currentUserContext,
                dataScopeService
        );
    }

    private InventoryReservationEntity reservation() {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(9001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(7001L);
        entity.setSourceNo("SO-7001");
        entity.setSourceLineId(8001L);
        entity.setReservedQty(new BigDecimal("5"));
        entity.setReleasedQty(new BigDecimal("1"));
        entity.setRemainingQty(new BigDecimal("4"));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private InventoryBalanceEntity balance(
            Long warehouseId,
            Long productId,
            String onHand,
            String reserved
    ) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(warehouseId);
        entity.setProductId(productId);
        entity.setQtyOnHand(new BigDecimal(onHand));
        entity.setQtyReserved(new BigDecimal(reserved));
        return entity;
    }

    private SalesOrderEntity salesOrder(Long accountBookId, String deliveryStatus) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(7001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus(deliveryStatus);
        return entity;
    }

    private SalesOrderLineEntity salesOrderLine(Long accountBookId) {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(8001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setOrderId(7001L);
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
