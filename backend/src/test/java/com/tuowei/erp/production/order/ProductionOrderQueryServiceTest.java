package com.tuowei.erp.production.order;

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
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
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

class ProductionOrderQueryServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9934L,
            101L,
            202L,
            11L,
            12L,
            "production_order_scope_user",
            "Production Order User"
    );
    private static final Long ORDER_ID = 6101L;
    private static final Long BOM_ID = 6201L;
    private static final Long PRODUCT_ID = 6301L;
    private static final Long MATERIAL_PRODUCT_ID = 6302L;
    private static final Long MATERIAL_WAREHOUSE_ID = 6401L;
    private static final Long FINISHED_WAREHOUSE_ID = 6402L;

    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderMaterialMapper materialMapper = mock(ProductionOrderMaterialMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionOrderEntity.class);
        initTableInfo(ProductionOrderMaterialEntity.class);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        stubEmptyPage();

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        assertThat(result.total()).isZero();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<ProductionOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(orderMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void listClampsPaginationAndNormalizesTenantFilters() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        stubEmptyPage();
        ProductionOrderPageQuery query = new ProductionOrderPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  MO-SCOPE  ");
        query.setStatus("  released  ");
        query.setBomId(BOM_ID);
        query.setProductId(PRODUCT_ID);
        query.setMaterialWarehouseId(MATERIAL_WAREHOUSE_ID);
        query.setFinishedWarehouseId(FINISHED_WAREHOUSE_ID);

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<ProductionOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);

        LambdaQueryWrapper<ProductionOrderEntity> wrapper = queryCaptor.getValue();
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("order_no")
                .contains("status")
                .contains("bom_id")
                .contains("product_id")
                .contains("material_warehouse_id")
                .contains("finished_warehouse_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId(),
                        "%MO-SCOPE%",
                        "RELEASED",
                        BOM_ID,
                        PRODUCT_ID,
                        MATERIAL_WAREHOUSE_ID,
                        FINISHED_WAREHOUSE_ID
                )
                .doesNotContain("%  MO-SCOPE  %", "  released  ");
    }

    @ParameterizedTest(name = "{0} scope filters production orders by visible creators")
    @MethodSource("creatorScopeCases")
    void listAppliesCreatorDataScopes(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        stubEmptyPage();

        service().list(new ProductionOrderPageQuery());

        LambdaQueryWrapper<ProductionOrderEntity> wrapper = capturedListQuery();
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT)).contains("created_by");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).containsAll(expectedCreatorIds);
    }

    @Test
    void listAppliesWarehouseDataScopeToBothProductionWarehouses() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(
                false,
                false,
                false,
                false,
                Set.of(MATERIAL_WAREHOUSE_ID, FINISHED_WAREHOUSE_ID)
        );
        stubScope(snapshot, Set.of(), Set.of());
        stubEmptyPage();

        service().list(new ProductionOrderPageQuery());

        LambdaQueryWrapper<ProductionOrderEntity> wrapper = capturedListQuery();
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("material_warehouse_id")
                .contains("finished_warehouse_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(MATERIAL_WAREHOUSE_ID, FINISHED_WAREHOUSE_ID);
    }

    @Test
    void listRejectsAllRowsWhenNoDataScopeIsAvailable() {
        stubScope(DataScopeSnapshot.none(), Set.of(), Set.of());
        stubEmptyPage();

        service().list(new ProductionOrderPageQuery());

        assertThat(capturedListQuery().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("1 = 0");
    }

    @Test
    void getByIdScopesMaterialQueryAndMapsDetail() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubCurrentUser(snapshot);
        ProductionOrderEntity order = activeOrder();
        order.setCreatedBy(null);
        ProductionOrderMaterialEntity material = material();
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));

        var result = service().getById(ORDER_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderMaterialEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materialMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<ProductionOrderMaterialEntity> wrapper = queryCaptor.getValue();
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("order_id")
                .contains("line_no");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), ORDER_ID);
        verify(dataScopeService).assertCanViewProductionOrder(
                order,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        assertThat(result.id()).isEqualTo(ORDER_ID);
        assertThat(result.orderNo()).isEqualTo("MO-6101");
        assertThat(result.materials()).singleElement().satisfies(mappedMaterial -> {
            assertThat(mappedMaterial.id()).isEqualTo(material.getId());
            assertThat(mappedMaterial.lineNo()).isEqualTo(1);
            assertThat(mappedMaterial.materialProductId()).isEqualTo(MATERIAL_PRODUCT_ID);
            assertThat(mappedMaterial.requiredQty()).isEqualByComparingTo("12.0000");
            assertThat(mappedMaterial.issuedQty()).isEqualByComparingTo("2.0000");
            assertThat(mappedMaterial.issuedAmount()).isEqualByComparingTo("30.00");
            assertThat(mappedMaterial.remark()).isEqualTo("query detail material");
        });
    }

    @Test
    void getByIdRejectsMissingOrderBeforeLoadingMaterials() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service().getById(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产工单不存在");

        verify(materialMapper, never()).selectList(any());
        verify(dataScopeService, never()).assertCanViewProductionOrder(any(), any(), any(), any(), any());
    }

    @Test
    void getByIdRejectsDeletedOrderBeforeLoadingMaterials() {
        ProductionOrderEntity order = activeOrder();
        order.setDeletedFlag(1);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().getById(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产工单不存在");

        verify(materialMapper, never()).selectList(any());
        verify(dataScopeService, never()).assertCanViewProductionOrder(any(), any(), any(), any(), any());
    }

    private static Stream<Arguments> creatorScopeCases() {
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

    private ProductionOrderQueryService service() {
        return new ProductionOrderQueryService(
                orderMapper,
                materialMapper,
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

    private void stubEmptyPage() {
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
    }

    private LambdaQueryWrapper<ProductionOrderEntity> capturedListQuery() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), queryCaptor.capture());
        return queryCaptor.getValue();
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

    private ProductionOrderEntity activeOrder() {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(ORDER_ID);
        order.setCompanyId(CURRENT_USER.companyId());
        order.setAccountBookId(CURRENT_USER.accountBookId());
        order.setOrderNo("MO-6101");
        order.setBomId(BOM_ID);
        order.setProductId(PRODUCT_ID);
        order.setMaterialWarehouseId(MATERIAL_WAREHOUSE_ID);
        order.setFinishedWarehouseId(FINISHED_WAREHOUSE_ID);
        order.setPlannedQty(new BigDecimal("10.0000"));
        order.setCompletedQty(new BigDecimal("2.0000"));
        order.setPlannedStartDate(LocalDate.of(2026, 8, 19));
        order.setPlannedFinishDate(LocalDate.of(2026, 8, 20));
        order.setStatus("RELEASED");
        order.setIssuedAmount(new BigDecimal("30.00"));
        order.setFinishedAmount(new BigDecimal("20.00"));
        order.setRemark("query detail");
        order.setDeletedFlag(0);
        return order;
    }

    private ProductionOrderMaterialEntity material() {
        ProductionOrderMaterialEntity material = new ProductionOrderMaterialEntity();
        material.setId(6501L);
        material.setCompanyId(CURRENT_USER.companyId());
        material.setAccountBookId(CURRENT_USER.accountBookId());
        material.setOrderId(ORDER_ID);
        material.setLineNo(1);
        material.setMaterialProductId(MATERIAL_PRODUCT_ID);
        material.setRequiredQty(new BigDecimal("12.0000"));
        material.setIssuedQty(new BigDecimal("2.0000"));
        material.setIssuedAmount(new BigDecimal("30.00"));
        material.setRemark("query detail material");
        return material;
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
