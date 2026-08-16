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
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
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

class SalesOrderQueryServiceTest {

    private static final Long WAREHOUSE_ID = 3201L;
    private static final Long CUSTOMER_ID = 3101L;
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9933L,
            101L,
            202L,
            11L,
            12L,
            "sales_order_scope_user",
            "销售订单用户"
    );

    private final SalesOrderMapper salesOrderMapper = mock(SalesOrderMapper.class);
    private final SalesOrderLineMapper salesOrderLineMapper = mock(SalesOrderLineMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is applied to normalized order filters")
    @MethodSource("scopedQueryCases")
    void listNormalizesFiltersAndAppliesSharedScope(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(salesOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
        SalesOrderPageQuery query = new SalesOrderPageQuery();
        query.setKeyword("  SO-SCOPE  ");
        query.setStatus("  draft  ");
        query.setApprovalStatus("  in_approval  ");
        query.setCustomerId(CUSTOMER_ID);

        service(new DataScopeService(null, null, null, null)).list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderMapper).selectPage(any(), queryCaptor.capture());
        assertNormalizedScope(queryCaptor.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndMapsOrderSummary() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        SalesOrderEntity order = order();
        when(salesOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1L);
            return page;
        });
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(customer()));
        SalesOrderPageQuery query = new SalesOrderPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(order.getId());
            assertThat(summary.orderNo()).isEqualTo("SO-3401");
            assertThat(summary.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(summary.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(summary.customerName()).isEqualTo("Scoped Customer");
            assertThat(summary.status()).isEqualTo("DRAFT");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<SalesOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(salesOrderMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        when(salesOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesOrderEntity> page = invocation.getArgument(0);
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
        SalesOrderEntity order = order();
        order.setCreatedBy(null);
        SalesOrderLineEntity line = orderLine();
        when(salesOrderMapper.selectById(order.getId())).thenReturn(order);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(line));
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer());

        var result = service(dataScopeService).getById(order.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("order_id")
                .contains("line_no");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), order.getId());
        verify(dataScopeService).assertCanViewSalesOrder(
                order,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        assertThat(result.id()).isEqualTo(order.getId());
        assertThat(result.customerName()).isEqualTo("Scoped Customer");
        assertThat(result.totalQuantity()).isEqualByComparingTo("3.0000");
        assertThat(result.totalAmount()).isEqualByComparingTo("60.00");
        assertThat(result.totalTaxAmount()).isEqualByComparingTo("7.80");
        assertThat(result.lines()).singleElement().satisfies(mappedLine -> {
            assertThat(mappedLine.id()).isEqualTo(line.getId());
            assertThat(mappedLine.lineNo()).isEqualTo(1);
            assertThat(mappedLine.productId()).isEqualTo(3301L);
            assertThat(mappedLine.qty()).isEqualByComparingTo("3.0000");
            assertThat(mappedLine.auxQty()).isEqualByComparingTo("1.0000");
            assertThat(mappedLine.auxUnitName()).isEqualTo("箱");
            assertThat(mappedLine.conversionFactor()).isEqualByComparingTo("3");
            assertThat(mappedLine.price()).isEqualByComparingTo("20.00");
            assertThat(mappedLine.taxRate()).isEqualByComparingTo("0.1300");
            assertThat(mappedLine.amount()).isEqualByComparingTo("60.00");
            assertThat(mappedLine.taxAmount()).isEqualByComparingTo("7.80");
            assertThat(mappedLine.deliveredQty()).isEqualByComparingTo("1.0000");
            assertThat(mappedLine.remark()).isEqualTo("detail line");
        });
    }

    @Test
    void getByIdRejectsMissingOrderBeforeLoadingLines() {
        when(salesOrderMapper.selectById(3401L)).thenReturn(null);

        assertThatThrownBy(() -> service(mock(DataScopeService.class)).getById(3401L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单不存在");

        verify(salesOrderLineMapper, never()).selectList(any());
    }

    @Test
    void assertCanViewPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        SalesOrderEntity order = order();
        order.setCreatedBy(7701L);
        when(userMapper.selectById(7701L)).thenReturn(user(7701L, 31L, 32L));

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewSalesOrder(
                order,
                CURRENT_USER,
                snapshot,
                31L,
                32L
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

    private SalesOrderQueryService service(DataScopeService dataScopeService) {
        return new SalesOrderQueryService(
                salesOrderMapper,
                salesOrderLineMapper,
                customerMapper,
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
            LambdaQueryWrapper<SalesOrderEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("deleted_flag")
                .contains("order_no")
                .contains("status")
                .contains("approval_status")
                .contains("customer_id")
                .contains("company_id")
                .contains("account_book_id")
                .contains("created_by");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%SO-SCOPE%",
                        "DRAFT",
                        "IN_APPROVAL",
                        CUSTOMER_ID,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain(
                        "%  SO-SCOPE  %",
                        "  draft  ",
                        "  in_approval  "
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

    private SalesOrderEntity order() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(3401L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("SO-3401");
        entity.setCustomerId(CUSTOMER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setOrderDate(LocalDate.of(2026, 6, 8));
        entity.setDeliveryDate(LocalDate.of(2026, 6, 9));
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("DRAFT");
        entity.setDeliveryStatus("NOT_DELIVERED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("60.00"));
        entity.setTotalTaxAmount(new BigDecimal("7.80"));
        entity.setRemark("query detail");
        entity.setCreatedBy(7701L);
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderLineEntity orderLine() {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(3501L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderId(3401L);
        entity.setLineNo(1);
        entity.setProductId(3301L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setAuxQty(new BigDecimal("1.0000"));
        entity.setAuxUnitName("箱");
        entity.setConversionFactor(new BigDecimal("3"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("60.00"));
        entity.setTaxAmount(new BigDecimal("7.80"));
        entity.setDeliveredQty(new BigDecimal("1.0000"));
        entity.setRemark("detail line");
        return entity;
    }

    private CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCustomerName("Scoped Customer");
        return customer;
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
