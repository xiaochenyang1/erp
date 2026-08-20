package com.tuowei.erp.masterdata;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.service.CustomerCommandService;
import com.tuowei.erp.masterdata.customer.service.CustomerQueryService;
import com.tuowei.erp.masterdata.customer.service.CustomerService;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductCommandService;
import com.tuowei.erp.masterdata.product.service.ProductQueryService;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.service.SupplierService;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MasterdataServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9951L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 23, 0)
    );

    private static final Long ENTITY_ID = 6101L;
    private static final Long DEPT_ID = 6201L;
    private static final Long MANAGER_ID = 6301L;

    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductEntity.class);
        initTableInfo(CustomerEntity.class);
        initTableInfo(SupplierEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(DeptEntity.class);
        initTableInfo(UserEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryLotBalanceEntity.class);
    }

    @Test
    void productListScopesByCompanyAndAccountBook() {
        stubAudit();
        ProductMapper mapper = mock(ProductMapper.class);
        Page<ProductEntity> page = page();
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page);

        productService(mapper).list(new ProductPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void productDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        ProductMapper mapper = mock(ProductMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(product(999L));

        assertThatThrownBy(() -> productService(mapper).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在");
    }

    @Test
    void customerListScopesByCompanyAndAccountBook() {
        stubAudit();
        CustomerMapper mapper = mock(CustomerMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        customerService(mapper).list(new CustomerPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void customerListSupportsTypeFilter() {
        stubAudit();
        CustomerMapper mapper = mock(CustomerMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());
        CustomerPageQuery query = new CustomerPageQuery();
        query.setType("COMPANY");

        customerService(mapper).list(query);

        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT)).contains("customer_type");
    }

    @Test
    void customerDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        CustomerMapper mapper = mock(CustomerMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(customer(999L));

        assertThatThrownBy(() -> customerService(mapper).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");
    }

    @Test
    void customerCreatePersistsTypeAndEmail() {
        stubAudit();
        CustomerMapper mapper = mock(CustomerMapper.class);
        when(mapper.insert(any(CustomerEntity.class))).thenReturn(1);

        var response = customerService(mapper).create(new CustomerCreateRequest(
                "C001",
                "tenant customer",
                "COMPANY",
                "Alice",
                "13800138000",
                "alice@example.com",
                "BANK_TRANSFER",
                new BigDecimal("1000.00"),
                30,
                "Shanghai",
                "ACTIVE",
                "remark"
        ));

        ArgumentCaptor<CustomerEntity> entityCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCustomerType()).isEqualTo("COMPANY");
        assertThat(entityCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.customerType()).isEqualTo("COMPANY");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void supplierListScopesByCompanyAndAccountBook() {
        stubAudit();
        SupplierMapper mapper = mock(SupplierMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        supplierService(mapper).list(new SupplierPageQuery());

        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void supplierDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        SupplierMapper mapper = mock(SupplierMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(supplier(999L));

        assertThatThrownBy(() -> supplierService(mapper).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");
    }

    @Test
    void supplierCreatePersistsEmailCreditPeriodAndSettlementMethod() {
        stubAudit();
        SupplierMapper mapper = mock(SupplierMapper.class);
        when(mapper.insert(any(SupplierEntity.class))).thenReturn(1);

        var response = supplierService(mapper).create(new SupplierCreateRequest(
                "S001",
                "tenant supplier",
                "Bob",
                "13900139000",
                "bob@example.com",
                "BANK_TRANSFER",
                30,
                "Suzhou",
                "ACTIVE",
                "remark"
        ));

        ArgumentCaptor<SupplierEntity> entityCaptor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEmail()).isEqualTo("bob@example.com");
        assertThat(entityCaptor.getValue().getCreditPeriod()).isEqualTo(30);
        assertThat(entityCaptor.getValue().getSettlementMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(response.email()).isEqualTo("bob@example.com");
        assertThat(response.creditPeriod()).isEqualTo(30);
        assertThat(response.settlementMethod()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    void warehouseListScopesByCompanyAndAccountBook() {
        stubAudit();
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page());

        warehouseService(mapper, mock(DeptMapper.class), mock(UserMapper.class)).list(new WarehousePageQuery());

        ArgumentCaptor<LambdaQueryWrapper<WarehouseEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void warehouseDetailRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        when(mapper.selectById(ENTITY_ID)).thenReturn(warehouse(999L));

        assertThatThrownBy(() -> warehouseService(mapper, mock(DeptMapper.class), mock(UserMapper.class)).getById(ENTITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在");
    }

    @Test
    void warehouseCreateRejectsDepartmentFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        DeptMapper deptMapper = mock(DeptMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(999L));
        when(userMapper.selectById(MANAGER_ID)).thenReturn(user(AUDIT.accountBookId()));
        when(warehouseMapper.insert(any(WarehouseEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> warehouseService(warehouseMapper, deptMapper, userMapper).create(warehouseCreateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");
    }

    @Test
    void warehouseCreateRejectsManagerFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        DeptMapper deptMapper = mock(DeptMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(userMapper.selectById(MANAGER_ID)).thenReturn(user(999L));
        when(warehouseMapper.insert(any(WarehouseEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> warehouseService(warehouseMapper, deptMapper, userMapper).create(warehouseCreateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("负责人不存在");
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private ProductService productService(ProductMapper mapper) {
        ProductQueryService queryService = new ProductQueryService(mapper, auditMetadataFactory);
        ProductCommandService commandService = new ProductCommandService(
                mapper,
                mock(InventoryBalanceMapper.class),
                mock(InventoryLotBalanceMapper.class),
                auditMetadataFactory,
                mock(SystemDictService.class),
                queryService
        );
        return new ProductService(queryService, commandService);
    }

    private CustomerService customerService(CustomerMapper mapper) {
        CustomerQueryService queryService = new CustomerQueryService(mapper, auditMetadataFactory);
        CustomerCommandService commandService = new CustomerCommandService(
                mapper,
                auditMetadataFactory,
                queryService
        );
        return new CustomerService(queryService, commandService);
    }

    private SupplierService supplierService(SupplierMapper mapper) {
        return new SupplierService(mapper, auditMetadataFactory);
    }

    private WarehouseService warehouseService(WarehouseMapper mapper, DeptMapper deptMapper, UserMapper userMapper) {
        return new WarehouseService(mapper, deptMapper, userMapper, auditMetadataFactory, org.mockito.Mockito.mock(LocationService.class));
    }

    private <T> Page<T> page() {
        Page<T> page = new Page<>(1, 20);
        page.setRecords(List.of());
        return page;
    }

    private ProductEntity product(Long accountBookId) {
        ProductEntity entity = new ProductEntity();
        entity.setId(ENTITY_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setProductCode("P001");
        entity.setProductName("tenant product");
        entity.setProductType("STANDARD");
        entity.setCategoryName("tenant");
        entity.setUnitName("件");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private CustomerEntity customer(Long accountBookId) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(ENTITY_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setCustomerCode("C001");
        entity.setCustomerName("tenant customer");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SupplierEntity supplier(Long accountBookId) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(ENTITY_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setSupplierCode("S001");
        entity.setSupplierName("tenant supplier");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity warehouse(Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(ENTITY_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseCode("W001");
        entity.setWarehouseName("tenant warehouse");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private DeptEntity dept(Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(DEPT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setDeptCode("D001");
        entity.setDeptName("tenant dept");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserEntity user(Long accountBookId) {
        UserEntity entity = new UserEntity();
        entity.setId(MANAGER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setUsername("tenant_manager");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseCreateRequest warehouseCreateRequest() {
        return new WarehouseCreateRequest(
                "W001",
                "tenant warehouse",
                DEPT_ID,
                MANAGER_ID,
                "address",
                "tenant boundary"
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
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
