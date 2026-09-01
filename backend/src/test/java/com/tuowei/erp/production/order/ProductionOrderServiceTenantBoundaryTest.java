package com.tuowei.erp.production.order;

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
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderNumberService;
import com.tuowei.erp.production.order.service.ProductionOrderPostingService;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionOrderServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9921L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 20, 0)
    );
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            AUDIT.userId(),
            AUDIT.companyId(),
            AUDIT.accountBookId(),
            11L,
            12L,
            "production_user",
            "production_user"
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
    private static final Long BOM_ID = 3001L;
    private static final Long FINISHED_PRODUCT_ID = 4001L;
    private static final Long MATERIAL_PRODUCT_ID = 4002L;
    private static final Long MATERIAL_WAREHOUSE_ID = 5001L;
    private static final Long FINISHED_WAREHOUSE_ID = 5002L;

    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderMaterialMapper materialMapper = mock(ProductionOrderMaterialMapper.class);
    private final ProductionOrderNumberService numberService = mock(ProductionOrderNumberService.class);
    private final ProductionBomService bomService = mock(ProductionBomService.class);
    private final InventoryPostingService inventoryPostingService = mock(InventoryPostingService.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ProductionOperationService productionOperationService = mock(ProductionOperationService.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionOrderEntity.class);
        initTableInfo(ProductionOrderMaterialEntity.class);
    }

    @Test
    void listScopesOrderQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(scopedUserResolver.resolve(CURRENT_USER, PRINCIPAL.dataScopeSnapshot()))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of()));
        when(dataScopeService.applyProductionOrderScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionOrderPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void getByIdScopesMaterialQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        ProductionOrderEntity order = activeOrder(6001L, AUDIT.companyId(), AUDIT.accountBookId());
        when(orderMapper.selectById(order.getId())).thenReturn(order);
        when(materialMapper.selectList(any())).thenReturn(List.of());

        service().getById(order.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderMaterialEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materialMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("order_id")
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void createRejectsFinishedProductFromDifferentAccountBookWithinSameCompany() {
        stubCreateDependencies();
        when(productValidator.requireProduct(eq(FINISHED_PRODUCT_ID), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        stubCreateDependencies();
        when(warehouseMapper.selectById(MATERIAL_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(MATERIAL_WAREHOUSE_ID, AUDIT.companyId(), 999L));
        when(warehouseMapper.selectById(FINISHED_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(FINISHED_WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void updateScopesMaterialDeleteByCompanyAndAccountBook() {
        stubUpdateDependencies();
        ProductionOrderEntity order = activeOrder(6001L, AUDIT.companyId(), AUDIT.accountBookId());
        when(orderMapper.selectById(order.getId())).thenReturn(order);

        service().update(order.getId(), updateRequest());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionOrderMaterialEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materialMapper).delete(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("order_id")
                .contains("company_id")
                .contains("account_book_id");
    }

    private void stubCreateDependencies() {
        stubAudit();
        stubCurrentUser();
        when(bomService.requireBom(BOM_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(activeBom());
        when(bomService.selectLines(BOM_ID))
                .thenReturn(List.of(activeBomLine()));
        when(warehouseMapper.selectById(MATERIAL_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(MATERIAL_WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(warehouseMapper.selectById(FINISHED_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(FINISHED_WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(numberService.nextOrderNo(createRequest().plannedStartDate())).thenReturn("MO-9921");
        when(orderMapper.insert(any(ProductionOrderEntity.class))).thenAnswer(invocation -> {
            ProductionOrderEntity order = invocation.getArgument(0);
            order.setId(6001L);
            return 1;
        });
        when(materialMapper.insert(any(ProductionOrderMaterialEntity.class))).thenReturn(1);
        when(materialMapper.selectList(any())).thenReturn(List.of());
    }

    private void stubUpdateDependencies() {
        stubAudit();
        stubCurrentUser();
        when(bomService.requireBom(BOM_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(activeBom());
        when(bomService.selectLines(BOM_ID))
                .thenReturn(List.of(activeBomLine()));
        when(warehouseMapper.selectById(MATERIAL_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(MATERIAL_WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(warehouseMapper.selectById(FINISHED_WAREHOUSE_ID))
                .thenReturn(activeWarehouse(FINISHED_WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(orderMapper.updateById(any(ProductionOrderEntity.class))).thenReturn(1);
        when(materialMapper.insert(any(ProductionOrderMaterialEntity.class))).thenReturn(1);
        when(materialMapper.selectList(any())).thenReturn(List.of());
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    @Test
    void releaseStopsAtAttachmentGateBeforeReservingMaterials() {
        stubAudit();
        stubCurrentUser();
        ProductionOrderEntity order = activeOrder(6001L, AUDIT.companyId(), AUDIT.accountBookId());
        when(orderMapper.selectById(6001L)).thenReturn(order);
        doThrow(new IllegalArgumentException("业务类型 PRODUCTION_ORDER 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("PRODUCTION_ORDER", 6001L);

        assertThatThrownBy(() -> service().release(6001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRODUCTION_ORDER");

        assertThat(order.getStatus()).isEqualTo(ProductionOrderService.STATUS_DRAFT);
        verify(materialMapper, never()).selectList(any());
        verify(inventoryPostingService, never()).reserve(any(), any(), any());
        verify(orderMapper, never()).updateById(any(ProductionOrderEntity.class));
    }

    private ProductionOrderService service() {
        ProductionOrderQueryService queryService = new ProductionOrderQueryService(
                orderMapper,
                materialMapper,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
        ProductionOrderPostingService postingService = new ProductionOrderPostingService(
                orderMapper,
                inventoryPostingService,
                auditMetadataFactory,
                queryService,
                productionOperationService,
                attachmentService
        );
        return new ProductionOrderService(
                orderMapper,
                materialMapper,
                numberService,
                bomService,
                productValidator,
                warehouseMapper,
                auditMetadataFactory,
                queryService,
                postingService
        );
    }

    private ProductionOrderCreateRequest createRequest() {
        return new ProductionOrderCreateRequest(
                BOM_ID,
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                BigDecimal.TEN,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                "tenant boundary"
        );
    }

    private ProductionOrderUpdateRequest updateRequest() {
        return new ProductionOrderUpdateRequest(
                FINISHED_WAREHOUSE_ID,
                MATERIAL_WAREHOUSE_ID,
                BigDecimal.TEN,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                "tenant boundary update"
        );
    }

    private ProductionBomEntity activeBom() {
        ProductionBomEntity bom = new ProductionBomEntity();
        bom.setId(BOM_ID);
        bom.setCompanyId(AUDIT.companyId());
        bom.setAccountBookId(AUDIT.accountBookId());
        bom.setProductId(FINISHED_PRODUCT_ID);
        bom.setBaseQty(BigDecimal.ONE);
        bom.setStatus("ACTIVE");
        bom.setDeletedFlag(0);
        return bom;
    }

    private ProductionBomLineEntity activeBomLine() {
        ProductionBomLineEntity line = new ProductionBomLineEntity();
        line.setId(7001L);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setBomId(BOM_ID);
        line.setLineNo(1);
        line.setMaterialProductId(MATERIAL_PRODUCT_ID);
        line.setQtyPer(BigDecimal.ONE);
        line.setLossRate(BigDecimal.ZERO);
        return line;
    }

    private ProductionOrderEntity activeOrder(Long id, Long companyId, Long accountBookId) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(id);
        order.setCompanyId(companyId);
        order.setAccountBookId(accountBookId);
        order.setOrderNo("MO-9921");
        order.setBomId(BOM_ID);
        order.setProductId(FINISHED_PRODUCT_ID);
        order.setMaterialWarehouseId(MATERIAL_WAREHOUSE_ID);
        order.setFinishedWarehouseId(FINISHED_WAREHOUSE_ID);
        order.setPlannedQty(BigDecimal.TEN);
        order.setCompletedQty(BigDecimal.ZERO);
        order.setPlannedStartDate(LocalDate.of(2026, 6, 8));
        order.setPlannedFinishDate(LocalDate.of(2026, 6, 9));
        order.setStatus(ProductionOrderService.STATUS_DRAFT);
        order.setIssuedAmount(BigDecimal.ZERO);
        order.setFinishedAmount(BigDecimal.ZERO);
        order.setDeletedFlag(0);
        return order;
    }

    private WarehouseEntity activeWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(companyId);
        warehouse.setAccountBookId(accountBookId);
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
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
