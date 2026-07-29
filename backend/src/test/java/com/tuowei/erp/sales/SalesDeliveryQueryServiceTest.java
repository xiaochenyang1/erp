package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesDeliveryQueryServiceTest {

    private static final Long ORDER_ID = 7101L;
    private static final Long WAREHOUSE_ID = 7201L;
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 6, 30);
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9933L,
            101L,
            202L,
            11L,
            12L,
            "sales_delivery_scope_user",
            "销售出库用户"
    );

    private final SalesDeliveryMapper salesDeliveryMapper = mock(SalesDeliveryMapper.class);
    private final SalesDeliveryLineMapper salesDeliveryLineMapper = mock(SalesDeliveryLineMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is applied to normalized delivery filters")
    @MethodSource("scopedQueryCases")
    void listNormalizesFiltersAndAppliesSharedScope(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(salesDeliveryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesDeliveryEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
        SalesDeliveryPageQuery query = new SalesDeliveryPageQuery();
        query.setKeyword("  SD-SCOPE  ");
        query.setOrderId(ORDER_ID);
        query.setWarehouseId(WAREHOUSE_ID);
        query.setStatus("  draft  ");
        query.setLogisticsStatus("  in_transit  ");
        query.setTrackingNo("  TRACK-9  ");
        query.setDeliveryDateFrom(DATE_FROM);
        query.setDeliveryDateTo(DATE_TO);

        service(new DataScopeService(null, null, null, null)).list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryMapper).selectPage(any(), queryCaptor.capture());
        assertNormalizedScope(queryCaptor.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndMapsDeliverySummary() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        SalesDeliveryEntity delivery = delivery();
        when(salesDeliveryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesDeliveryEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(delivery));
            page.setTotal(1L);
            return page;
        });
        SalesDeliveryPageQuery query = new SalesDeliveryPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(delivery.getId());
            assertThat(summary.deliveryNo()).isEqualTo("SD-7301");
            assertThat(summary.orderId()).isEqualTo(ORDER_ID);
            assertThat(summary.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(summary.carrierName()).isEqualTo("Scoped Carrier");
            assertThat(summary.trackingNo()).isEqualTo("TRACK-9");
            assertThat(summary.logisticsStatus()).isEqualTo("IN_TRANSIT");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<SalesDeliveryEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(salesDeliveryMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        when(salesDeliveryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesDeliveryEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        var result = service(new DataScopeService(null, null, null, null)).list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
    }

    @Test
    void getByIdScopesLineQueryAndMapsFullDetail() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesDeliveryEntity delivery = delivery();
        delivery.setCreatedBy(null);
        SalesDeliveryLineEntity line = deliveryLine();
        when(salesDeliveryMapper.selectById(delivery.getId())).thenReturn(delivery);
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(line));

        var result = service(dataScopeService).getById(delivery.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("delivery_id")
                .contains("line_no");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), delivery.getId());
        verify(dataScopeService).assertCanViewSalesDelivery(
                delivery,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        assertThat(result.id()).isEqualTo(delivery.getId());
        assertThat(result.totalQuantity()).isEqualByComparingTo("3.0000");
        assertThat(result.totalAmount()).isEqualByComparingTo("60.00");
        assertThat(result.totalTaxAmount()).isEqualByComparingTo("7.80");
        assertThat(result.lines()).singleElement().satisfies(mappedLine -> {
            assertThat(mappedLine.id()).isEqualTo(line.getId());
            assertThat(mappedLine.orderLineId()).isEqualTo(7401L);
            assertThat(mappedLine.productId()).isEqualTo(7501L);
            assertThat(mappedLine.qty()).isEqualByComparingTo("3.0000");
            assertThat(mappedLine.price()).isEqualByComparingTo("20.00");
            assertThat(mappedLine.taxRate()).isEqualByComparingTo("0.1300");
            assertThat(mappedLine.amount()).isEqualByComparingTo("60.00");
            assertThat(mappedLine.taxAmount()).isEqualByComparingTo("7.80");
            assertThat(mappedLine.returnedQty()).isEqualByComparingTo("1.0000");
            assertThat(mappedLine.lotNo()).isEqualTo("LOT-SCOPE");
            assertThat(mappedLine.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(mappedLine.expiryDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(mappedLine.locationId()).isEqualTo(7601L);
            assertThat(mappedLine.serialNos()).isEqualTo("SN-1\nSN-2\nSN-3");
            assertThat(mappedLine.remark()).isEqualTo("detail line");
        });
    }

    @Test
    void getByIdRejectsMissingDeliveryBeforeLoadingLines() {
        when(salesDeliveryMapper.selectById(7301L)).thenReturn(null);

        assertThatThrownBy(() -> service(mock(DataScopeService.class)).getById(7301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售出库单不存在");

        verify(salesDeliveryLineMapper, never()).selectList(any());
    }

    @Test
    void assertCanViewDeliveryPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesDeliveryEntity delivery = delivery();
        delivery.setCreatedBy(7701L);
        when(userMapper.selectById(7701L)).thenReturn(user(7701L, 31L, 32L));

        service(dataScopeService).assertCanView(delivery);

        verify(dataScopeService).assertCanViewSalesDelivery(
                delivery,
                CURRENT_USER,
                snapshot,
                31L,
                32L
        );
    }

    @Test
    void assertCanViewSalesOrderPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(ORDER_ID);
        order.setCompanyId(CURRENT_USER.companyId());
        order.setAccountBookId(CURRENT_USER.accountBookId());
        order.setCreatedBy(7801L);
        when(userMapper.selectById(7801L)).thenReturn(user(7801L, 41L, 42L));

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewSalesOrder(
                order,
                CURRENT_USER,
                snapshot,
                41L,
                42L
        );
    }

    private static Stream<Arguments> scopedQueryCases() {
        return Stream.of(
                Arguments.of(
                        "SELF",
                        new DataScopeSnapshot(false, false, false, true, Set.of()),
                        Set.of(),
                        Set.of(),
                        Set.of(CURRENT_USER.userId())
                ),
                Arguments.of(
                        "DEPT",
                        new DataScopeSnapshot(false, true, false, false, Set.of()),
                        Set.of(9301L, 9302L),
                        Set.of(),
                        Set.of(9301L, 9302L)
                ),
                Arguments.of(
                        "POST",
                        new DataScopeSnapshot(false, false, true, false, Set.of()),
                        Set.of(),
                        Set.of(9401L, 9402L),
                        Set.of(9401L, 9402L)
                )
        );
    }

    private SalesDeliveryQueryService service(DataScopeService dataScopeService) {
        return new SalesDeliveryQueryService(
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
    }

    private void stubScope(
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        stubCurrentUser(snapshot);
        when(scopedUserResolver.resolve(CURRENT_USER, snapshot))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(deptUserIds, postUserIds));
    }

    private void stubCurrentUser(DataScopeSnapshot snapshot) {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
    }

    private void assertNormalizedScope(
            LambdaQueryWrapper<SalesDeliveryEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("deleted_flag")
                .contains("delivery_no")
                .contains("order_id")
                .contains("warehouse_id")
                .contains("status")
                .contains("logistics_status")
                .contains("tracking_no")
                .contains("delivery_date")
                .contains("company_id")
                .contains("account_book_id")
                .contains("created_by");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%SD-SCOPE%",
                        ORDER_ID,
                        WAREHOUSE_ID,
                        "DRAFT",
                        "IN_TRANSIT",
                        "%TRACK-9%",
                        DATE_FROM,
                        DATE_TO,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain(
                        "%  SD-SCOPE  %",
                        "  draft  ",
                        "  in_transit  ",
                        "%  TRACK-9  %"
                );
    }

    private ErpPrincipal principal(DataScopeSnapshot snapshot) {
        return new ErpPrincipal(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                CURRENT_USER.deptId(),
                CURRENT_USER.postId(),
                CURRENT_USER.username(),
                CURRENT_USER.realName(),
                "N/A",
                Set.of(),
                snapshot
        );
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(7301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setDeliveryNo("SD-7301");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setDeliveryDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("60.00"));
        entity.setTotalTaxAmount(new BigDecimal("7.80"));
        entity.setRemark("query detail");
        entity.setCarrierName("Scoped Carrier");
        entity.setTrackingNo("TRACK-9");
        entity.setLogisticsStatus("IN_TRANSIT");
        entity.setCreatedBy(7701L);
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(7351L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setDeliveryId(7301L);
        entity.setLineNo(1);
        entity.setOrderLineId(7401L);
        entity.setProductId(7501L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("60.00"));
        entity.setTaxAmount(new BigDecimal("7.80"));
        entity.setReturnedQty(new BigDecimal("1.0000"));
        entity.setLotNo("LOT-SCOPE");
        entity.setProductionDate(LocalDate.of(2026, 5, 1));
        entity.setExpiryDate(LocalDate.of(2027, 5, 1));
        entity.setLocationId(7601L);
        entity.setSerialNos("SN-1\nSN-2\nSN-3");
        entity.setRemark("detail line");
        return entity;
    }

    private UserEntity user(Long id, Long deptId, Long postId) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setDeptId(deptId);
        entity.setPostId(postId);
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
