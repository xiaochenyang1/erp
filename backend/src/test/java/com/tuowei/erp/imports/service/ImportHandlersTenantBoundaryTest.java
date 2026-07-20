package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.imports.service.ImportTypeHandler.ImportValidationContext;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ImportHandlersTenantBoundaryTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9944L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(CustomerEntity.class);
        initTableInfo(ProductEntity.class);
        initTableInfo(SupplierEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(DeptEntity.class);
        initTableInfo(UserEntity.class);
        initTableInfo(ReceivableEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
    }

    @Test
    void customerImportScopesDuplicateLookupByCompanyAndAccountBook() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);

        new CustomerImportHandler(support(), customerMapper)
                .validate(1, Map.of("customer_code", "C001", "customer_name", "tenant customer"), context());

        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper).selectCount(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void productImportScopesDuplicateLookupByCompanyAndAccountBook() {
        ProductMapper productMapper = mock(ProductMapper.class);
        when(productMapper.selectCount(any())).thenReturn(0L);

        new ProductImportHandler(support(), productMapper)
                .validate(1, Map.of("product_code", "P001", "product_name", "tenant product", "unit_name", "件"), context());

        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectCount(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void supplierImportScopesDuplicateLookupByCompanyAndAccountBook() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        when(supplierMapper.selectCount(any())).thenReturn(0L);

        new SupplierImportHandler(support(), supplierMapper)
                .validate(1, Map.of("supplier_code", "S001", "supplier_name", "tenant supplier"), context());

        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(supplierMapper).selectCount(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void warehouseImportScopesWarehouseDeptAndManagerLookupsByCompanyAndAccountBook() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        DeptMapper deptMapper = mock(DeptMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(warehouseMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectCount(any())).thenReturn(1L);
        when(userMapper.selectCount(any())).thenReturn(1L);

        new WarehouseImportHandler(support(), warehouseMapper, deptMapper, userMapper)
                .validate(1, Map.of(
                        "warehouse_code", "W001",
                        "warehouse_name", "tenant warehouse",
                        "dept_id", "3101",
                        "manager_user_id", "3201"
                ), context());

        ArgumentCaptor<LambdaQueryWrapper<WarehouseEntity>> warehouseWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(warehouseMapper).selectCount(warehouseWrapper.capture());
        assertTenantScoped(warehouseWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<DeptEntity>> deptWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deptMapper).selectCount(deptWrapper.capture());
        assertTenantScoped(deptWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> userWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectCount(userWrapper.capture());
        assertTenantScoped(userWrapper.getValue());
    }

    @Test
    void openingReceivableImportScopesCustomerAndReceivableLookupsByCompanyAndAccountBook() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
        when(customerMapper.selectOne(any())).thenReturn(activeCustomer());
        when(receivableMapper.selectCount(any())).thenReturn(0L);

        new OpeningReceivableImportHandler(support(), customerMapper, receivableMapper)
                .validate(1, Map.of(
                        "customer_code", "C001",
                        "receivable_no", "AR001",
                        "biz_date", "2026-06-08",
                        "original_amount", "100.00",
                        "settled_amount", "0.00"
                ), context());

        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> customerWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper).selectOne(customerWrapper.capture());
        assertTenantScoped(customerWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> receivableWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectCount(receivableWrapper.capture());
        assertTenantScoped(receivableWrapper.getValue());
    }

    @Test
    void openingPayableImportScopesSupplierAndPayableLookupsByCompanyAndAccountBook() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        PayableMapper payableMapper = mock(PayableMapper.class);
        when(supplierMapper.selectOne(any())).thenReturn(activeSupplier());
        when(payableMapper.selectCount(any())).thenReturn(0L);

        new OpeningPayableImportHandler(support(), supplierMapper, payableMapper)
                .validate(1, Map.of(
                        "supplier_code", "S001",
                        "payable_no", "AP001",
                        "biz_date", "2026-06-08",
                        "original_amount", "100.00",
                        "settled_amount", "0.00"
                ), context());

        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> supplierWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(supplierMapper).selectOne(supplierWrapper.capture());
        assertTenantScoped(supplierWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectCount(payableWrapper.capture());
        assertTenantScoped(payableWrapper.getValue());
    }

    @Test
    void openingInventoryImportScopesWarehouseProductAndBalanceLookupsByCompanyAndAccountBook() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
        when(warehouseMapper.selectOne(any())).thenReturn(activeWarehouse());
        when(productMapper.selectOne(any())).thenReturn(activeProduct());
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(null);

        new OpeningInventoryImportHandler(
                support(),
                warehouseMapper,
                productMapper,
                inventoryBalanceMapper,
                mock(InventoryTransactionMapper.class),
                mock(InventoryPostingService.class)
        ).validate(1, Map.of(
                "warehouse_code", "W001",
                "product_code", "P001",
                "qty_on_hand", "1.0000",
                "amount_on_hand", "10.00",
                "opening_date", "2026-06-08"
        ), context());

        ArgumentCaptor<LambdaQueryWrapper<WarehouseEntity>> warehouseWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(warehouseMapper).selectOne(warehouseWrapper.capture());
        assertTenantScoped(warehouseWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> productWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectOne(productWrapper.capture());
        assertTenantScoped(productWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectOne(balanceWrapper.capture());
        assertTenantScoped(balanceWrapper.getValue());
    }

    private ImportValidationSupport support() {
        return new ImportValidationSupport(new ObjectMapper());
    }

    private ImportValidationContext context() {
        return new ImportValidationContext(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID);
    }

    private CustomerEntity activeCustomer() {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(4101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SupplierEntity activeSupplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(4201L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity activeWarehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(4301L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setWarehouseCode("W001");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity activeProduct() {
        ProductEntity entity = new ProductEntity();
        entity.setId(4401L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setProductCode("P001");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setLotControlled(0);
        entity.setShelfLifeControlled(0);
        return entity;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id");
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
