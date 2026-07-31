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
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
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

class SalesReturnQueryServiceTest {

    private static final Long DELIVERY_ID = 7101L;
    private static final Long WAREHOUSE_ID = 7201L;
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 6, 30);
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9934L,
            101L,
            202L,
            11L,
            12L,
            "sales_return_scope_user",
            "销售退货用户"
    );

    private final SalesReturnMapper salesReturnMapper = mock(SalesReturnMapper.class);
    private final SalesReturnLineMapper salesReturnLineMapper = mock(SalesReturnLineMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesReturnEntity.class);
        initTableInfo(SalesReturnLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is applied to normalized sales return filters")
    @MethodSource("scopedQueryCases")
    void listNormalizesFiltersAndAppliesSharedScope(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(salesReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesReturnEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
        SalesReturnPageQuery query = new SalesReturnPageQuery();
        query.setKeyword("  SR-SCOPE  ");
        query.setDeliveryId(DELIVERY_ID);
        query.setWarehouseId(WAREHOUSE_ID);
        query.setStatus("  posted  ");
        query.setReturnDateFrom(DATE_FROM);
        query.setReturnDateTo(DATE_TO);

        service(new DataScopeService(null, null, null, null)).list(query);

        ArgumentCaptor<LambdaQueryWrapper<SalesReturnEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesReturnMapper).selectPage(any(), queryCaptor.capture());
        assertNormalizedScope(queryCaptor.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndMapsSummaryWithoutLines() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        SalesReturnEntity salesReturn = salesReturn();
        when(salesReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesReturnEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(salesReturn));
            page.setTotal(1L);
            return page;
        });
        SalesReturnPageQuery query = new SalesReturnPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(salesReturn.getId());
            assertThat(summary.returnNo()).isEqualTo("SR-7301");
            assertThat(summary.deliveryId()).isEqualTo(DELIVERY_ID);
            assertThat(summary.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(summary.totalQuantity()).isEqualByComparingTo("3.0000");
            assertThat(summary.totalAmount()).isEqualByComparingTo("60.00");
            assertThat(summary.totalTaxAmount()).isEqualByComparingTo("7.80");
            assertThat(summary.lines()).isEmpty();
        });

        ArgumentCaptor<Page<SalesReturnEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(salesReturnMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        when(salesReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesReturnEntity> page = invocation.getArgument(0);
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
        SalesReturnEntity salesReturn = salesReturn();
        salesReturn.setCreatedBy(null);
        SalesReturnLineEntity line = salesReturnLine();
        when(salesReturnMapper.selectById(salesReturn.getId())).thenReturn(salesReturn);
        when(salesReturnLineMapper.selectList(any())).thenReturn(List.of(line));

        var result = service(dataScopeService).getById(salesReturn.getId());

        ArgumentCaptor<LambdaQueryWrapper<SalesReturnLineEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesReturnLineMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("return_id")
                .contains("line_no");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), salesReturn.getId());
        verify(dataScopeService).assertCanViewSalesReturn(
                salesReturn,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        assertThat(result.id()).isEqualTo(salesReturn.getId());
        assertThat(result.lines()).singleElement().satisfies(mappedLine -> {
            assertThat(mappedLine.id()).isEqualTo(line.getId());
            assertThat(mappedLine.deliveryLineId()).isEqualTo(7401L);
            assertThat(mappedLine.orderLineId()).isEqualTo(7501L);
            assertThat(mappedLine.productId()).isEqualTo(7601L);
            assertThat(mappedLine.qty()).isEqualByComparingTo("3.0000");
            assertThat(mappedLine.price()).isEqualByComparingTo("20.00");
            assertThat(mappedLine.taxRate()).isEqualByComparingTo("0.1300");
            assertThat(mappedLine.amount()).isEqualByComparingTo("60.00");
            assertThat(mappedLine.taxAmount()).isEqualByComparingTo("7.80");
            assertThat(mappedLine.lotNo()).isEqualTo("LOT-RETURN");
            assertThat(mappedLine.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(mappedLine.expiryDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(mappedLine.locationId()).isEqualTo(7701L);
            assertThat(mappedLine.serialNos()).isEqualTo("SN-R1\nSN-R2\nSN-R3");
            assertThat(mappedLine.remark()).isEqualTo("return detail line");
        });
    }

    @Test
    void getByIdRejectsMissingReturnBeforeLoadingLines() {
        when(salesReturnMapper.selectById(7301L)).thenReturn(null);

        assertThatThrownBy(() -> service(mock(DataScopeService.class)).getById(7301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售退货单不存在");

        verify(salesReturnLineMapper, never()).selectList(any());
    }

    @Test
    void assertCanViewReturnPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesReturnEntity salesReturn = salesReturn();
        salesReturn.setCreatedBy(7801L);
        when(userMapper.selectById(7801L)).thenReturn(user(7801L, 31L, 32L));

        service(dataScopeService).assertCanView(salesReturn);

        verify(dataScopeService).assertCanViewSalesReturn(
                salesReturn,
                CURRENT_USER,
                snapshot,
                31L,
                32L
        );
    }

    @Test
    void assertCanViewDeliveryPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(DELIVERY_ID);
        delivery.setCompanyId(CURRENT_USER.companyId());
        delivery.setAccountBookId(CURRENT_USER.accountBookId());
        delivery.setCreatedBy(7802L);
        when(userMapper.selectById(7802L)).thenReturn(user(7802L, 41L, 42L));

        service(dataScopeService).assertCanView(delivery);

        verify(dataScopeService).assertCanViewSalesDelivery(
                delivery,
                CURRENT_USER,
                snapshot,
                41L,
                42L
        );
    }

    @Test
    void assertCanViewOrderPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(7901L);
        order.setCompanyId(CURRENT_USER.companyId());
        order.setAccountBookId(CURRENT_USER.accountBookId());
        order.setCreatedBy(7803L);
        when(userMapper.selectById(7803L)).thenReturn(user(7803L, 51L, 52L));

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewSalesOrder(
                order,
                CURRENT_USER,
                snapshot,
                51L,
                52L
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

    private SalesReturnQueryService service(DataScopeService dataScopeService) {
        return new SalesReturnQueryService(
                salesReturnMapper,
                salesReturnLineMapper,
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
            LambdaQueryWrapper<SalesReturnEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("deleted_flag")
                .contains("return_no")
                .contains("delivery_id")
                .contains("warehouse_id")
                .contains("status")
                .contains("return_date")
                .contains("company_id")
                .contains("account_book_id")
                .contains("created_by");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%SR-SCOPE%",
                        DELIVERY_ID,
                        WAREHOUSE_ID,
                        "POSTED",
                        DATE_FROM,
                        DATE_TO,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain("%  SR-SCOPE  %", "  posted  ");
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

    private SalesReturnEntity salesReturn() {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setId(7301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnNo("SR-7301");
        entity.setDeliveryId(DELIVERY_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReturnDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("60.00"));
        entity.setTotalTaxAmount(new BigDecimal("7.80"));
        entity.setRemark("query detail");
        entity.setCreatedBy(7801L);
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesReturnLineEntity salesReturnLine() {
        SalesReturnLineEntity entity = new SalesReturnLineEntity();
        entity.setId(7351L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnId(7301L);
        entity.setLineNo(1);
        entity.setDeliveryLineId(7401L);
        entity.setOrderLineId(7501L);
        entity.setProductId(7601L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("60.00"));
        entity.setTaxAmount(new BigDecimal("7.80"));
        entity.setLotNo("LOT-RETURN");
        entity.setProductionDate(LocalDate.of(2026, 5, 1));
        entity.setExpiryDate(LocalDate.of(2027, 5, 1));
        entity.setLocationId(7701L);
        entity.setSerialNos("SN-R1\nSN-R2\nSN-R3");
        entity.setRemark("return detail line");
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
