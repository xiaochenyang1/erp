package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import com.tuowei.erp.sales.order.service.SalesPriceEvaluator;
import com.tuowei.erp.sales.order.service.SalesOrderNumberService;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.workflow.service.WorkflowService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            AUDIT.userId(),
            AUDIT.companyId(),
            AUDIT.accountBookId(),
            11L,
            12L,
            "sales_order_scope_user",
            "销售订单用户"
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
    private static final Long CUSTOMER_ID = 3101L;
    private static final Long WAREHOUSE_ID = 3201L;
    private static final Long PRODUCT_ID = 3301L;

    private final SalesOrderMapper salesOrderMapper = mock(SalesOrderMapper.class);
    private final SalesOrderLineMapper salesOrderLineMapper = mock(SalesOrderLineMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final InventoryPostingService inventoryPostingService = mock(InventoryPostingService.class);
    private final SalesOrderNumberService salesOrderNumberService = mock(SalesOrderNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final SalesCreditEvaluator salesCreditEvaluator = mock(SalesCreditEvaluator.class);
    private final SalesPriceEvaluator salesPriceEvaluator = mock(SalesPriceEvaluator.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(salesOrderMapper.selectById(3401L)).thenReturn(order());
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().getById(3401L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void createRejectsCustomerFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(customerMapper.selectById(CUSTOMER_ID))
                .thenReturn(activeCustomer(CUSTOMER_ID, AUDIT.companyId(), 999L));
        stubValidWarehouseAndProduct();
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在或已停用");
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(customerMapper.selectById(CUSTOMER_ID))
                .thenReturn(activeCustomer(CUSTOMER_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(warehouseMapper.selectById(WAREHOUSE_ID))
                .thenReturn(activeWarehouse(WAREHOUSE_ID, AUDIT.companyId(), 999L));
        when(productMapper.selectById(PRODUCT_ID))
                .thenReturn(activeProduct(PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(customerMapper.selectById(CUSTOMER_ID))
                .thenReturn(activeCustomer(CUSTOMER_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(warehouseMapper.selectById(WAREHOUSE_ID))
                .thenReturn(activeWarehouse(WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void unapproveApprovedOrderReleasesReservationsAndReturnsToDraft() {
        stubCurrentUser();
        stubAudit();
        SalesOrderEntity approved = order();
        approved.setStatus("APPROVED");
        approved.setApprovalStatus("APPROVED");
        approved.setDeliveryStatus("NOT_DELIVERED");
        when(salesOrderMapper.selectById(3401L)).thenReturn(approved);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().unapprove(3401L);

        verify(inventoryPostingService).releaseAllReservations("SALES_ORDER", 3401L, AUDIT);
        assertThat(approved.getStatus()).isEqualTo("DRAFT");
        assertThat(approved.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        verify(salesOrderMapper).updateById(approved);
    }

    @Test
    void unapproveRejectsDeliveredOrderWithoutReleasingReservation() {
        stubCurrentUser();
        SalesOrderEntity delivered = order();
        delivered.setStatus("APPROVED");
        delivered.setApprovalStatus("APPROVED");
        delivered.setDeliveryStatus("PARTIAL_DELIVERED");
        when(salesOrderMapper.selectById(3401L)).thenReturn(delivered);

        assertThatThrownBy(() -> service().unapprove(3401L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已出库销售订单不允许反审核");
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubValidWarehouseAndProduct() {
        when(warehouseMapper.selectById(WAREHOUSE_ID))
                .thenReturn(activeWarehouse(WAREHOUSE_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(productMapper.selectById(PRODUCT_ID))
                .thenReturn(activeProduct(PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()));
    }

    private void stubOrderInsert() {
        when(salesOrderMapper.insert(any(SalesOrderEntity.class))).thenAnswer(invocation -> {
            SalesOrderEntity order = invocation.getArgument(0);
            order.setId(3401L);
            return 1;
        });
    }

    private SalesOrderService service() {
        return new SalesOrderService(
                salesOrderMapper,
                salesOrderLineMapper,
                customerMapper,
                productMapper,
                productValidator,
                warehouseMapper,
                inventoryPostingService,
                salesOrderNumberService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper,
                workflowService,
                salesCreditEvaluator,
                salesPriceEvaluator
        );
    }

    private SalesOrderCreateRequest createRequest() {
        return new SalesOrderCreateRequest(
                CUSTOMER_ID,
                WAREHOUSE_ID,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                "tenant boundary",
                List.of(new SalesOrderLineRequest(
                        PRODUCT_ID,
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        "line"
                ))
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
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
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderLineEntity orderLine() {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(3501L);
        entity.setOrderId(3401L);
        entity.setLineNo(1);
        entity.setProductId(PRODUCT_ID);
        entity.setQty(BigDecimal.ONE);
        entity.setPrice(BigDecimal.TEN);
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(BigDecimal.TEN);
        entity.setTaxAmount(BigDecimal.ZERO);
        entity.setDeliveredQty(BigDecimal.ZERO);
        return entity;
    }

    private CustomerEntity activeCustomer(Long id, Long companyId, Long accountBookId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(id);
        customer.setCompanyId(companyId);
        customer.setAccountBookId(accountBookId);
        customer.setCustomerName("tenant customer");
        customer.setStatus("ACTIVE");
        customer.setDeletedFlag(0);
        return customer;
    }

    private WarehouseEntity activeWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(companyId);
        warehouse.setAccountBookId(accountBookId);
        warehouse.setWarehouseName("tenant warehouse");
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private ProductEntity activeProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCompanyId(companyId);
        product.setAccountBookId(accountBookId);
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
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
