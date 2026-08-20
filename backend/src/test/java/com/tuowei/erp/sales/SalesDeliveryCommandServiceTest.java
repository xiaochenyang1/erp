package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryCommandService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryNumberService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLineRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLogisticsUpdateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryCommandServiceTest {

    private static final Long DELIVERY_ID = 7001L;
    private static final Long OTHER_DELIVERY_ID = 7002L;
    private static final Long ORDER_ID = 7101L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final Long FIRST_ORDER_LINE_ID = 8001L;
    private static final Long SECOND_ORDER_LINE_ID = 8002L;
    private static final Long FIRST_PRODUCT_ID = 4001L;
    private static final Long SECOND_PRODUCT_ID = 4002L;
    private static final LocalDate DELIVERY_DATE = LocalDate.of(2026, 6, 8);
    private static final AuditMetadata AUDIT = new AuditMetadata(
            9501L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 15, 0)
    );

    @Mock private SalesDeliveryMapper salesDeliveryMapper;
    @Mock private SalesDeliveryLineMapper salesDeliveryLineMapper;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private SalesOrderLineMapper salesOrderLineMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private InventoryReservationMapper inventoryReservationMapper;
    @Mock private SalesDeliveryNumberService salesDeliveryNumberService;
    @Mock private SalesDeliveryQueryService salesDeliveryQueryService;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private ProductValidator productValidator;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(InventoryReservationEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void createMapsHeaderLinesAmountsAuditAndDefaultLogistics() {
        SalesOrderLineEntity firstOrderLine = orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "8.0000", "0.0000", "10.00", "13.0000");
        SalesOrderLineEntity secondOrderLine = orderLine(
                SECOND_ORDER_LINE_ID, SECOND_PRODUCT_ID, "6.0000", "1.0000", "20.00", "5.0000");
        stubCreateCalculation(List.of(firstOrderLine, secondOrderLine), "20.0000");
        when(salesDeliveryNumberService.nextDeliveryNo(DELIVERY_DATE)).thenReturn("SD-20260608-001");
        doAnswer(invocation -> {
            SalesDeliveryEntity entity = invocation.getArgument(0);
            entity.setId(DELIVERY_ID);
            return 1;
        }).when(salesDeliveryMapper).insert(any(SalesDeliveryEntity.class));
        SalesDeliveryResponse expected = mock(SalesDeliveryResponse.class);
        when(salesDeliveryQueryService.toResponse(any(SalesDeliveryEntity.class), anyList()))
                .thenReturn(expected);

        SalesDeliveryLineRequest firstRequest = lineRequest(
                FIRST_ORDER_LINE_ID, "2.0000", "LOT-A", 3101L, "SN-A", "first line");
        SalesDeliveryLineRequest secondRequest = lineRequest(
                SECOND_ORDER_LINE_ID, "1.5000", "LOT-B", 3102L, "SN-B", "second line");
        SalesDeliveryCreateRequest request = new SalesDeliveryCreateRequest(
                ORDER_ID,
                WAREHOUSE_ID,
                DELIVERY_DATE,
                "delivery remark",
                "顺丰",
                "SF1001",
                null,
                List.of(firstRequest, secondRequest)
        );

        SalesDeliveryResponse result = service().create(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<SalesDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(SalesDeliveryEntity.class);
        verify(salesDeliveryMapper).insert(deliveryCaptor.capture());
        SalesDeliveryEntity insertedDelivery = deliveryCaptor.getValue();
        assertThat(insertedDelivery.getId()).isEqualTo(DELIVERY_ID);
        assertThat(insertedDelivery.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(insertedDelivery.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(insertedDelivery.getDeliveryNo()).isEqualTo("SD-20260608-001");
        assertThat(insertedDelivery.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(insertedDelivery.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(insertedDelivery.getDeliveryDate()).isEqualTo(DELIVERY_DATE);
        assertThat(insertedDelivery.getStatus()).isEqualTo("DRAFT");
        assertThat(insertedDelivery.getTotalQuantity()).isEqualByComparingTo("3.5000");
        assertThat(insertedDelivery.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(insertedDelivery.getTotalTaxAmount()).isEqualByComparingTo("4.10");
        assertThat(insertedDelivery.getDeletedFlag()).isZero();
        assertThat(insertedDelivery.getRemark()).isEqualTo("delivery remark");
        assertThat(insertedDelivery.getCarrierName()).isEqualTo("顺丰");
        assertThat(insertedDelivery.getTrackingNo()).isEqualTo("SF1001");
        assertThat(insertedDelivery.getLogisticsStatus()).isEqualTo("PENDING_SHIP");
        assertThat(insertedDelivery.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(insertedDelivery.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(insertedDelivery.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(insertedDelivery.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(insertedDelivery.getVersion()).isZero();

        ArgumentCaptor<SalesDeliveryLineEntity> lineCaptor = ArgumentCaptor.forClass(SalesDeliveryLineEntity.class);
        verify(salesDeliveryLineMapper, times(2)).insert(lineCaptor.capture());
        List<SalesDeliveryLineEntity> insertedLines = lineCaptor.getAllValues();
        assertDeliveryLine(
                insertedLines.get(0), 1, FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID,
                "2.0000", "10.00", "13.0000", "20.00", "2.60",
                "LOT-A", 3101L, "SN-A", "first line");
        assertDeliveryLine(
                insertedLines.get(1), 2, SECOND_ORDER_LINE_ID, SECOND_PRODUCT_ID,
                "1.5000", "20.00", "5.0000", "30.00", "1.50",
                "LOT-B", 3102L, "SN-B", "second line");
        verify(productValidator).requireProducts(
                List.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID), AUDIT.companyId(), AUDIT.accountBookId());
        verify(salesDeliveryQueryService).assertCanView(same(insertedDelivery));
        verify(salesDeliveryQueryService).toResponse(same(insertedDelivery), eq(insertedLines));

        InOrder writes = inOrder(salesDeliveryMapper, productValidator, salesDeliveryLineMapper);
        writes.verify(salesDeliveryMapper).insert(same(insertedDelivery));
        writes.verify(productValidator).requireProducts(
                List.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID), AUDIT.companyId(), AUDIT.accountBookId());
        writes.verify(salesDeliveryLineMapper, times(2)).insert(any(SalesDeliveryLineEntity.class));
    }

    @Test
    void createScopesOrderDraftAndReservationQueriesByTenant() {
        stubCreateCalculation(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "8.0000", "0.0000", "10.00", "0.0000")), "8.0000");
        when(salesDeliveryNumberService.nextDeliveryNo(DELIVERY_DATE)).thenReturn("SD-20260608-002");
        doAnswer(invocation -> {
            SalesDeliveryEntity entity = invocation.getArgument(0);
            entity.setId(DELIVERY_ID);
            return 1;
        }).when(salesDeliveryMapper).insert(any(SalesDeliveryEntity.class));

        service().create(createRequest(List.of(simpleLine(FIRST_ORDER_LINE_ID, "2.0000")), null));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> orderLineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper).selectList(orderLineQueryCaptor.capture());
        assertTenantScoped(orderLineQueryCaptor.getValue());
        assertThat(parameters(orderLineQueryCaptor.getValue())).contains(ORDER_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryEntity>> draftQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryMapper).selectList(draftQueryCaptor.capture());
        assertTenantScoped(draftQueryCaptor.getValue());
        assertThat(parameters(draftQueryCaptor.getValue())).contains(ORDER_ID, "DRAFT", 0);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryReservationMapper).selectList(reservationQueryCaptor.capture());
        assertTenantScoped(reservationQueryCaptor.getValue());
        assertThat(parameters(reservationQueryCaptor.getValue()))
                .contains("SALES_ORDER", FIRST_ORDER_LINE_ID, "ACTIVE");
    }

    @Test
    void createRejectsMissingOrderBeforeAnyDownstreamWork() {
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单不存在");

        verifyNoInteractions(
                auditMetadataFactory, warehouseMapper, salesOrderLineMapper,
                inventoryReservationMapper, salesDeliveryNumberService, productValidator,
                salesDeliveryLineMapper
        );
        verifyNoDeliveryWritesOrResponse();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "APPROVED, IN_APPROVAL",
            "SUBMITTED, APPROVED"
    })
    void createRequiresBothApprovedLifecycleFields(String status, String approvalStatus) {
        SalesOrderEntity order = approvedOrder();
        order.setStatus(status);
        order.setApprovalStatus(approvalStatus);
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单未审批通过，不能创建销售出库单");

        verify(salesDeliveryQueryService, never()).assertCanView(any(SalesOrderEntity.class));
        verifyNoInteractions(auditMetadataFactory, warehouseMapper, salesOrderLineMapper);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createStopsWhenOrderDataScopeCheckIsDenied() {
        SalesOrderEntity order = approvedOrder();
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        doThrow(new AccessDeniedException("无权访问销售订单"))
                .when(salesDeliveryQueryService).assertCanView(order);

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问销售订单");

        verifyNoInteractions(auditMetadataFactory, warehouseMapper, salesOrderLineMapper);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsWarehouseOutsideCurrentAccountBook() {
        SalesOrderEntity order = approvedOrder();
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID))
                .thenReturn(warehouse(WAREHOUSE_ID, AUDIT.companyId(), 9999L, "ACTIVE", 0));

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");

        verifyNoInteractions(salesOrderLineMapper, inventoryReservationMapper);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsWarehouseDifferentFromOrderReservationWarehouse() {
        Long otherWarehouseId = 3002L;
        SalesOrderEntity order = approvedOrder();
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(otherWarehouseId))
                .thenReturn(warehouse(otherWarehouseId, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0));
        SalesDeliveryCreateRequest request = new SalesDeliveryCreateRequest(
                ORDER_ID, otherWarehouseId, DELIVERY_DATE, null, null, null, null,
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000"))
        );

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售出库仓库必须与销售订单预占仓库一致");

        verifyNoInteractions(salesOrderLineMapper, inventoryReservationMapper);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsLineThatDoesNotBelongToOrder() {
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "5.0000", "0.0000", "10.00", "0.0000")));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(9999L, "1.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单明细不存在");

        verifyNoInteractions(inventoryReservationMapper, salesDeliveryNumberService, productValidator);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsAccumulatedQuantityBeyondOrderRemainingQuantity() {
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "5.0000", "1.0000", "10.00", "0.0000")));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(FIRST_ORDER_LINE_ID, "10.0000")));
        List<SalesDeliveryLineRequest> lines = List.of(
                simpleLine(FIRST_ORDER_LINE_ID, "2.5000"),
                simpleLine(FIRST_ORDER_LINE_ID, "2.0000")
        );

        assertThatThrownBy(() -> service().create(createRequest(lines, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("出库数量超过销售订单剩余可出库数量");

        verify(inventoryReservationMapper, times(1)).selectList(any());
        verifyNoInteractions(salesDeliveryNumberService, productValidator);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsAccumulatedQuantityBeyondActiveReservation() {
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "10.0000", "0.0000", "10.00", "0.0000")));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(FIRST_ORDER_LINE_ID, "3.0000")));
        List<SalesDeliveryLineRequest> lines = List.of(
                simpleLine(FIRST_ORDER_LINE_ID, "2.0000"),
                simpleLine(FIRST_ORDER_LINE_ID, "2.0000")
        );

        assertThatThrownBy(() -> service().create(createRequest(lines, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单预占数量不足，不能创建销售出库单");

        verify(inventoryReservationMapper, times(2)).selectList(any());
        verifyNoInteractions(salesDeliveryNumberService, productValidator);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createSubtractsOtherDraftDeliveriesFromAvailableReservation() {
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "10.0000", "0.0000", "10.00", "0.0000")));
        SalesDeliveryEntity otherDraft = delivery(OTHER_DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of(otherDraft));
        when(salesDeliveryLineMapper.selectList(any()))
                .thenReturn(List.of(deliveryLine(OTHER_DELIVERY_ID, FIRST_ORDER_LINE_ID, "4.0000")));
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(FIRST_ORDER_LINE_ID, "5.0000")));

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "2.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单预占数量不足，不能创建销售出库单");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> occupiedLineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(occupiedLineQueryCaptor.capture());
        assertTenantScoped(occupiedLineQueryCaptor.getValue());
        assertThat(parameters(occupiedLineQueryCaptor.getValue()))
                .contains(OTHER_DELIVERY_ID, FIRST_ORDER_LINE_ID);
        verifyNoInteractions(salesDeliveryNumberService, productValidator);
        verifyNoDeliveryWritesOrResponse();
    }

    @Test
    void createRejectsUnsupportedLogisticsStatusBeforeWriting() {
        stubCreateCalculation(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "5.0000", "0.0000", "10.00", "0.0000")), "5.0000");
        when(salesDeliveryNumberService.nextDeliveryNo(DELIVERY_DATE)).thenReturn("SD-20260608-003");

        assertThatThrownBy(() -> service().create(createRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), "SHIPPED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("物流状态仅支持 PENDING_SHIP/PICKED_UP/IN_TRANSIT/DELIVERED");

        verify(salesDeliveryMapper, never()).insert(any(SalesDeliveryEntity.class));
        verifyNoInteractions(productValidator, salesDeliveryLineMapper);
        verify(salesDeliveryQueryService, never()).toResponse(any(), anyList());
    }

    @Test
    void updateReplacesDraftLinesAndExcludesCurrentDeliveryFromOccupiedQuantity() {
        SalesDeliveryEntity draft = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        SalesOrderLineEntity orderLine = orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "8.0000", "1.0000", "12.50", "13.0000");
        stubUpdateCalculation(draft, List.of(orderLine), "8.0000");
        when(salesDeliveryMapper.updateById(draft)).thenReturn(1);
        SalesDeliveryResponse expected = mock(SalesDeliveryResponse.class);
        when(salesDeliveryQueryService.toResponse(any(SalesDeliveryEntity.class), anyList()))
                .thenReturn(expected);
        SalesDeliveryLineRequest requestedLine = lineRequest(
                FIRST_ORDER_LINE_ID, "2.0000", "LOT-UPDATED", 3201L, "SN-UPDATED", "updated line");
        SalesDeliveryUpdateRequest request = new SalesDeliveryUpdateRequest(
                ORDER_ID,
                WAREHOUSE_ID,
                DELIVERY_DATE.plusDays(1),
                "updated delivery",
                "圆通",
                "YT2001",
                " in_transit ",
                List.of(requestedLine)
        );

        SalesDeliveryResponse result = service().update(DELIVERY_ID, request);

        assertThat(result).isSameAs(expected);
        assertThat(draft.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(draft.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(draft.getDeliveryDate()).isEqualTo(DELIVERY_DATE.plusDays(1));
        assertThat(draft.getTotalQuantity()).isEqualByComparingTo("2.0000");
        assertThat(draft.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(draft.getTotalTaxAmount()).isEqualByComparingTo("3.25");
        assertThat(draft.getRemark()).isEqualTo("updated delivery");
        assertThat(draft.getCarrierName()).isEqualTo("圆通");
        assertThat(draft.getTrackingNo()).isEqualTo("YT2001");
        assertThat(draft.getLogisticsStatus()).isEqualTo("IN_TRANSIT");
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getUpdatedTime()).isEqualTo(AUDIT.now());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryEntity>> draftQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryMapper).selectList(draftQueryCaptor.capture());
        assertTenantScoped(draftQueryCaptor.getValue());
        assertThat(draftQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT)).contains("<>");
        assertThat(parameters(draftQueryCaptor.getValue())).contains(DELIVERY_ID, ORDER_ID, "DRAFT", 0);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> deleteQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).delete(deleteQueryCaptor.capture());
        assertTenantScoped(deleteQueryCaptor.getValue());
        assertThat(parameters(deleteQueryCaptor.getValue())).contains(DELIVERY_ID);

        ArgumentCaptor<SalesDeliveryLineEntity> insertedLineCaptor =
                ArgumentCaptor.forClass(SalesDeliveryLineEntity.class);
        verify(salesDeliveryLineMapper).insert(insertedLineCaptor.capture());
        assertDeliveryLine(
                insertedLineCaptor.getValue(), 1, FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID,
                "2.0000", "12.50", "13.0000", "25.00", "3.25",
                "LOT-UPDATED", 3201L, "SN-UPDATED", "updated line");
        verify(productValidator).requireProducts(
                List.of(FIRST_PRODUCT_ID), AUDIT.companyId(), AUDIT.accountBookId());
        verify(salesDeliveryQueryService).toResponse(same(draft), eq(List.of(insertedLineCaptor.getValue())));

        InOrder writes = inOrder(salesDeliveryMapper, salesDeliveryLineMapper, productValidator, salesDeliveryQueryService);
        writes.verify(salesDeliveryMapper).updateById(same(draft));
        writes.verify(salesDeliveryLineMapper).delete(any());
        writes.verify(productValidator).requireProducts(
                List.of(FIRST_PRODUCT_ID), AUDIT.companyId(), AUDIT.accountBookId());
        writes.verify(salesDeliveryLineMapper).insert(same(insertedLineCaptor.getValue()));
        writes.verify(salesDeliveryQueryService).toResponse(same(draft), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POSTED", "CANCELLED"})
    void updateRejectsNonDraftDeliveryBeforeLoadingOrder(String status) {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, status, "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);

        assertThatThrownBy(() -> service().update(DELIVERY_ID, updateRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售出库单状态不允许编辑");

        verifyNoInteractions(salesOrderMapper, auditMetadataFactory, warehouseMapper, salesOrderLineMapper);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verifyNoLineReplacementOrResponse();
    }

    @Test
    void updateRejectsReservationShortageBeforeMutatingPersistence() {
        SalesDeliveryEntity draft = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(draft);
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "10.0000", "0.0000", "10.00", "0.0000")));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(FIRST_ORDER_LINE_ID, "1.0000")));

        assertThatThrownBy(() -> service().update(DELIVERY_ID, updateRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "2.0000")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单预占数量不足，不能创建销售出库单");

        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verifyNoLineReplacementOrResponse();
    }

    @Test
    void updateOptimisticLockConflictStopsLineReplacementAndResponse() {
        SalesDeliveryEntity draft = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        stubUpdateCalculation(draft, List.of(orderLine(
                FIRST_ORDER_LINE_ID, FIRST_PRODUCT_ID, "5.0000", "0.0000", "10.00", "0.0000")), "5.0000");
        when(salesDeliveryMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().update(DELIVERY_ID, updateRequest(
                List.of(simpleLine(FIRST_ORDER_LINE_ID, "1.0000")), null)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("销售出库单已被其他操作修改，请刷新后重试");

        verify(salesDeliveryLineMapper, never()).delete(any());
        verify(salesDeliveryLineMapper, never()).insert(any(SalesDeliveryLineEntity.class));
        verifyNoInteractions(productValidator);
        verify(salesDeliveryQueryService, never()).toResponse(any(), anyList());
    }

    @Test
    void cancelTransitionsDraftRefreshesAuditAndReturnsCurrentDetail() {
        SalesDeliveryEntity draft = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        SalesDeliveryResponse expected = mock(SalesDeliveryResponse.class);
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(draft);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(draft)).thenReturn(1);
        when(salesDeliveryQueryService.getById(DELIVERY_ID)).thenReturn(expected);

        SalesDeliveryResponse result = service().cancel(DELIVERY_ID);

        assertThat(result).isSameAs(expected);
        assertThat(draft.getStatus()).isEqualTo("CANCELLED");
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(salesDeliveryMapper, salesDeliveryQueryService, auditMetadataFactory);
        order.verify(salesDeliveryMapper).selectById(DELIVERY_ID);
        order.verify(salesDeliveryQueryService).assertCanView(same(draft));
        order.verify(auditMetadataFactory).current();
        order.verify(salesDeliveryMapper).updateById(same(draft));
        order.verify(salesDeliveryQueryService).getById(DELIVERY_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POSTED", "CANCELLED"})
    void cancelRejectsNonDraftDelivery(String status) {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, status, "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);

        assertThatThrownBy(() -> service().cancel(DELIVERY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售出库单状态不允许作废");

        verifyNoInteractions(auditMetadataFactory);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryQueryService, never()).getById(any());
    }

    @Test
    void cancelOptimisticLockConflictDoesNotReturnStaleDetail() {
        SalesDeliveryEntity draft = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(draft);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().cancel(DELIVERY_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("销售出库单已被其他操作修改，请刷新后重试");

        verify(salesDeliveryQueryService, never()).getById(any());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<null>, PENDING_SHIP",
            "PENDING_SHIP, PENDING_SHIP",
            "PENDING_SHIP, PICKED_UP",
            "PENDING_SHIP, IN_TRANSIT",
            "PENDING_SHIP, DELIVERED",
            "PICKED_UP, IN_TRANSIT",
            "PICKED_UP, DELIVERED",
            "IN_TRANSIT, DELIVERED",
            "DELIVERED, DELIVERED"
    }, nullValues = "<null>")
    void updateLogisticsAllowsSameOrForwardState(String current, String next) {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", current);
        SalesDeliveryResponse expected = mock(SalesDeliveryResponse.class);
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(delivery)).thenReturn(1);
        when(salesDeliveryQueryService.getById(DELIVERY_ID)).thenReturn(expected);

        SalesDeliveryResponse result = service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest(next, null, null, null));

        assertThat(result).isSameAs(expected);
        assertThat(delivery.getLogisticsStatus()).isEqualTo(next);
        assertThat(delivery.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(delivery.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(salesDeliveryMapper).updateById(same(delivery));
        verify(salesDeliveryQueryService).getById(DELIVERY_ID);
    }

    @Test
    void updateLogisticsNormalizesStatusAndTrimsOrClearsTransportFields() {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        delivery.setCarrierName("旧承运商");
        delivery.setTrackingNo("OLD-001");
        delivery.setRemark("old remark");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(delivery)).thenReturn(1);

        service().updateLogistics(DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest(
                "  picked_up  ", "  顺丰速运  ", "   ", "  remark keeps spaces  "));

        assertThat(delivery.getLogisticsStatus()).isEqualTo("PICKED_UP");
        assertThat(delivery.getCarrierName()).isEqualTo("顺丰速运");
        assertThat(delivery.getTrackingNo()).isNull();
        assertThat(delivery.getRemark()).isEqualTo("  remark keeps spaces  ");
    }

    @Test
    void updateLogisticsLeavesOptionalFieldsUntouchedWhenTheyAreNull() {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", "PICKED_UP");
        delivery.setCarrierName("existing carrier");
        delivery.setTrackingNo("EXISTING-001");
        delivery.setRemark("existing remark");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(delivery)).thenReturn(1);

        service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest("IN_TRANSIT", null, null, null));

        assertThat(delivery.getCarrierName()).isEqualTo("existing carrier");
        assertThat(delivery.getTrackingNo()).isEqualTo("EXISTING-001");
        assertThat(delivery.getRemark()).isEqualTo("existing remark");
    }

    @ParameterizedTest
    @CsvSource({
            "PICKED_UP, PENDING_SHIP",
            "IN_TRANSIT, PENDING_SHIP",
            "IN_TRANSIT, PICKED_UP",
            "DELIVERED, PENDING_SHIP",
            "DELIVERED, PICKED_UP",
            "DELIVERED, IN_TRANSIT"
    })
    void updateLogisticsRejectsEveryBackwardTransition(String current, String next) {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", current);
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);

        assertThatThrownBy(() -> service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest(next, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("物流状态不允许回退: " + current + " -> " + next);

        verifyNoInteractions(auditMetadataFactory);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryQueryService, never()).getById(any());
    }

    @Test
    void updateLogisticsRejectsUnsupportedStatus() {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);

        assertThatThrownBy(() -> service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest("SHIPPED", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("物流状态仅支持 PENDING_SHIP/PICKED_UP/IN_TRANSIT/DELIVERED");

        verifyNoInteractions(auditMetadataFactory);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryQueryService, never()).getById(any());
    }

    @Test
    void updateLogisticsRejectsCancelledDeliveryBeforeParsingStatus() {
        SalesDeliveryEntity cancelled = delivery(DELIVERY_ID, "CANCELLED", "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(cancelled);

        assertThatThrownBy(() -> service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest("SHIPPED", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已作废的发货单不能更新物流状态");

        verifyNoInteractions(auditMetadataFactory);
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryQueryService, never()).getById(any());
    }

    @Test
    void updateLogisticsAllowsPostedDeliveryToContinueShippingLifecycle() {
        SalesDeliveryEntity posted = delivery(DELIVERY_ID, "POSTED", "PICKED_UP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(posted);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(posted)).thenReturn(1);

        service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest("IN_TRANSIT", null, null, null));

        assertThat(posted.getLogisticsStatus()).isEqualTo("IN_TRANSIT");
        verify(salesDeliveryMapper).updateById(same(posted));
        verify(salesDeliveryQueryService).getById(DELIVERY_ID);
    }

    @Test
    void updateLogisticsOptimisticLockConflictDoesNotReturnStaleDetail() {
        SalesDeliveryEntity delivery = delivery(DELIVERY_ID, "DRAFT", "PENDING_SHIP");
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesDeliveryMapper.updateById(delivery)).thenReturn(0);

        assertThatThrownBy(() -> service().updateLogistics(
                DELIVERY_ID, new SalesDeliveryLogisticsUpdateRequest("PICKED_UP", null, null, null)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("销售出库单已被其他操作修改，请刷新后重试");

        verify(salesDeliveryQueryService, never()).getById(any());
    }

    private SalesDeliveryCommandService service() {
        return new SalesDeliveryCommandService(
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                warehouseMapper,
                inventoryReservationMapper,
                salesDeliveryNumberService,
                salesDeliveryQueryService,
                auditMetadataFactory,
                productValidator
        );
    }

    private void stubApprovedOrderAuditAndWarehouse() {
        SalesOrderEntity order = approvedOrder();
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse());
    }

    private void stubCreateCalculation(List<SalesOrderLineEntity> orderLines, String reservationQty) {
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(orderLines);
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any())).thenReturn(List.of(
                reservation(FIRST_ORDER_LINE_ID, reservationQty),
                reservation(SECOND_ORDER_LINE_ID, reservationQty)
        ));
    }

    private void stubUpdateCalculation(
            SalesDeliveryEntity delivery,
            List<SalesOrderLineEntity> orderLines,
            String reservationQty
    ) {
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        stubApprovedOrderAuditAndWarehouse();
        when(salesOrderLineMapper.selectList(any())).thenReturn(orderLines);
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any())).thenReturn(List.of(
                reservation(FIRST_ORDER_LINE_ID, reservationQty),
                reservation(SECOND_ORDER_LINE_ID, reservationQty)
        ));
    }

    private SalesDeliveryCreateRequest createRequest(List<SalesDeliveryLineRequest> lines, String logisticsStatus) {
        return new SalesDeliveryCreateRequest(
                ORDER_ID,
                WAREHOUSE_ID,
                DELIVERY_DATE,
                "remark",
                null,
                null,
                logisticsStatus,
                lines
        );
    }

    private SalesDeliveryUpdateRequest updateRequest(List<SalesDeliveryLineRequest> lines, String logisticsStatus) {
        return new SalesDeliveryUpdateRequest(
                ORDER_ID,
                WAREHOUSE_ID,
                DELIVERY_DATE,
                "updated remark",
                null,
                null,
                logisticsStatus,
                lines
        );
    }

    private SalesDeliveryLineRequest simpleLine(Long orderLineId, String qty) {
        return new SalesDeliveryLineRequest(
                orderLineId,
                new BigDecimal(qty),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private SalesDeliveryLineRequest lineRequest(
            Long orderLineId,
            String qty,
            String lotNo,
            Long locationId,
            String serialNos,
            String remark
    ) {
        return new SalesDeliveryLineRequest(
                orderLineId,
                new BigDecimal(qty),
                lotNo,
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2027, 1, 2),
                locationId,
                serialNos,
                remark
        );
    }

    private SalesOrderEntity approvedOrder() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOrderNo("SO-7101");
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderLineEntity orderLine(
            Long id,
            Long productId,
            String qty,
            String deliveredQty,
            String price,
            String taxRate
    ) {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOrderId(ORDER_ID);
        entity.setLineNo(id.equals(FIRST_ORDER_LINE_ID) ? 1 : 2);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setDeliveredQty(new BigDecimal(deliveredQty));
        entity.setPrice(new BigDecimal(price));
        entity.setTaxRate(new BigDecimal(taxRate));
        return entity;
    }

    private WarehouseEntity activeWarehouse() {
        return warehouse(WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0);
    }

    private WarehouseEntity warehouse(
            Long id,
            Long companyId,
            Long accountBookId,
            String status,
            Integer deletedFlag
    ) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setStatus(status);
        entity.setDeletedFlag(deletedFlag);
        return entity;
    }

    private InventoryReservationEntity reservation(Long sourceLineId, String remainingQty) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(9000L + sourceLineId);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(ORDER_ID);
        entity.setSourceLineId(sourceLineId);
        entity.setRemainingQty(new BigDecimal(remainingQty));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private SalesDeliveryEntity delivery(Long id, String status, String logisticsStatus) {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setDeliveryNo("SD-" + id);
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setDeliveryDate(DELIVERY_DATE);
        entity.setStatus(status);
        entity.setLogisticsStatus(logisticsStatus);
        entity.setDeletedFlag(0);
        entity.setCreatedBy(9001L);
        entity.setCreatedTime(AUDIT.now().minusDays(1));
        entity.setUpdatedBy(9001L);
        entity.setUpdatedTime(AUDIT.now().minusDays(1));
        entity.setVersion(3);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine(Long deliveryId, Long orderLineId, String qty) {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(8101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setDeliveryId(deliveryId);
        entity.setOrderLineId(orderLineId);
        entity.setQty(new BigDecimal(qty));
        return entity;
    }

    private void assertDeliveryLine(
            SalesDeliveryLineEntity line,
            int lineNo,
            Long orderLineId,
            Long productId,
            String qty,
            String price,
            String taxRate,
            String amount,
            String taxAmount,
            String lotNo,
            Long locationId,
            String serialNos,
            String remark
    ) {
        assertThat(line.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(line.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(line.getDeliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(line.getLineNo()).isEqualTo(lineNo);
        assertThat(line.getOrderLineId()).isEqualTo(orderLineId);
        assertThat(line.getProductId()).isEqualTo(productId);
        assertThat(line.getQty()).isEqualByComparingTo(qty);
        assertThat(line.getPrice()).isEqualByComparingTo(price);
        assertThat(line.getTaxRate()).isEqualByComparingTo(taxRate);
        assertThat(line.getAmount()).isEqualByComparingTo(amount);
        assertThat(line.getTaxAmount()).isEqualByComparingTo(taxAmount);
        assertThat(line.getReturnedQty()).isEqualByComparingTo("0.0000");
        assertThat(line.getLotNo()).isEqualTo(lotNo);
        assertThat(line.getProductionDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(line.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(line.getLocationId()).isEqualTo(locationId);
        assertThat(line.getSerialNos()).isEqualTo(serialNos);
        assertThat(line.getRemark()).isEqualTo(remark);
        assertThat(line.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(line.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(line.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(line.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(line.getVersion()).isZero();
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
        assertThat(parameters(wrapper)).contains(AUDIT.companyId(), AUDIT.accountBookId());
    }

    private Collection<Object> parameters(LambdaQueryWrapper<?> wrapper) {
        return wrapper.getParamNameValuePairs().values();
    }

    private void verifyNoDeliveryWritesOrResponse() {
        verify(salesDeliveryMapper, never()).insert(any(SalesDeliveryEntity.class));
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryLineMapper, never()).insert(any(SalesDeliveryLineEntity.class));
        verify(salesDeliveryQueryService, never()).toResponse(any(), anyList());
    }

    private void verifyNoLineReplacementOrResponse() {
        verify(salesDeliveryLineMapper, never()).delete(any());
        verify(salesDeliveryLineMapper, never()).insert(any(SalesDeliveryLineEntity.class));
        verify(salesDeliveryQueryService, never()).toResponse(any(), anyList());
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
