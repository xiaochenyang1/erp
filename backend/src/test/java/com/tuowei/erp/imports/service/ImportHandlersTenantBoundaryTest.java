package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.service.ImportTypeHandler.ImportValidationContext;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
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

import java.time.LocalDateTime;
import java.util.List;
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
        initTableInfo(LocationEntity.class);
    }

    @Test
    void customerImportScopesDuplicateLookupByCompanyAndAccountBook() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);

        new CustomerImportHandler(support(), customerMapper)
                .validate(1, Map.of(
                        "customer_code", "C001",
                        "customer_name", "tenant customer",
                        "customer_type", "COMPANY"
                ), context());

        ArgumentCaptor<LambdaQueryWrapper<CustomerEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customerMapper).selectCount(wrapper.capture());
        assertTenantScoped(wrapper.getValue());
    }

    @Test
    void customerImportValidatesAndPersistsProfileFields() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);
        ImportValidationSupport support = support();
        CustomerImportHandler handler = new CustomerImportHandler(support, customerMapper);

        ImportTypeHandler.ImportRowPlan plan = handler.validate(1, Map.of(
                "customer_code", "C002",
                "customer_name", "profile customer",
                "customer_type", "individual",
                "email", "customer@example.com",
                "credit_period", "45"
        ), context());

        assertThat(plan.valid()).isTrue();
        assertThat(plan.normalized())
                .containsEntry("customerType", "INDIVIDUAL")
                .containsEntry("email", "customer@example.com")
                .containsEntry("creditPeriod", 45);

        handler.commit(new ImportJobEntity(), List.of(row(support, plan)), audit());

        ArgumentCaptor<CustomerEntity> entity = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerMapper).insert(entity.capture());
        assertThat(entity.getValue().getCustomerType()).isEqualTo("INDIVIDUAL");
        assertThat(entity.getValue().getEmail()).isEqualTo("customer@example.com");
        assertThat(entity.getValue().getCreditPeriod()).isEqualTo(45);
    }

    @Test
    void customerImportRejectsInvalidCreditPeriod() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);
        CustomerImportHandler handler = new CustomerImportHandler(support(), customerMapper);

        ImportTypeHandler.ImportRowPlan nonNumeric = handler.validate(1, Map.of(
                "customer_code", "C005",
                "customer_name", "invalid customer",
                "customer_type", "COMPANY",
                "credit_period", "thirty"
        ), context());
        ImportTypeHandler.ImportRowPlan negative = handler.validate(2, Map.of(
                "customer_code", "C006",
                "customer_name", "negative customer",
                "customer_type", "COMPANY",
                "credit_period", "-1"
        ), context());

        assertThat(nonNumeric.valid()).isFalse();
        assertThat(nonNumeric.errors())
                .anySatisfy(error -> assertThat(error.column()).isEqualTo("credit_period"));
        assertThat(negative.valid()).isFalse();
        assertThat(negative.errors())
                .anySatisfy(error -> assertThat(error.message()).contains("不能小于0"));
    }

    @Test
    void customerImportRejectsUnsupportedCustomerType() {
        CustomerMapper customerMapper = mock(CustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);

        ImportTypeHandler.ImportRowPlan plan = new CustomerImportHandler(support(), customerMapper)
                .validate(1, Map.of(
                        "customer_code", "C003",
                        "customer_name", "invalid customer",
                        "customer_type", "UNKNOWN"
                ), context());

        assertThat(plan.valid()).isFalse();
        assertThat(plan.errors())
                .anySatisfy(error -> {
                    assertThat(error.column()).isEqualTo("customer_type");
                    assertThat(error.message()).contains("COMPANY").contains("INDIVIDUAL");
                });
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
    void productImportPersistsBarcodeAndControlFlags() {
        ProductMapper productMapper = mock(ProductMapper.class);
        when(productMapper.selectCount(any())).thenReturn(0L);
        ImportValidationSupport support = support();
        ProductImportHandler handler = new ProductImportHandler(support, productMapper);

        ImportTypeHandler.ImportRowPlan plan = handler.validate(1, Map.of(
                "product_code", "P-CTRL-001",
                "product_name", "controlled product",
                "unit_name", "件",
                "barcode", "6901234567890",
                "lot_controlled", "1",
                "shelf_life_controlled", "YES",
                "inspection_required", "true",
                "serial_controlled", "0"
        ), context());

        assertThat(plan.valid()).isTrue();
        assertThat(plan.normalized())
                .containsEntry("barcode", "6901234567890")
                .containsEntry("lotControlled", 1)
                .containsEntry("shelfLifeControlled", 1)
                .containsEntry("inspectionRequired", 1)
                .containsEntry("serialControlled", 0);

        handler.commit(new ImportJobEntity(), List.of(row(support, plan)), audit());

        ArgumentCaptor<ProductEntity> entity = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insert(entity.capture());
        assertThat(entity.getValue().getBarcode()).isEqualTo("6901234567890");
        assertThat(entity.getValue().getLotControlled()).isEqualTo(1);
        assertThat(entity.getValue().getShelfLifeControlled()).isEqualTo(1);
        assertThat(entity.getValue().getInspectionRequired()).isEqualTo(1);
        assertThat(entity.getValue().getSerialControlled()).isEqualTo(0);
    }

    @Test
    void productImportRejectsShelfLifeWithoutLotControl() {
        ProductMapper productMapper = mock(ProductMapper.class);
        when(productMapper.selectCount(any())).thenReturn(0L);

        ImportTypeHandler.ImportRowPlan plan = new ProductImportHandler(support(), productMapper)
                .validate(1, Map.of(
                        "product_code", "P-CTRL-002",
                        "product_name", "invalid shelf life",
                        "unit_name", "件",
                        "lot_controlled", "0",
                        "shelf_life_controlled", "1"
                ), context());

        assertThat(plan.valid()).isFalse();
        assertThat(plan.errors())
                .anySatisfy(error -> {
                    assertThat(error.column()).isEqualTo("shelf_life_controlled");
                    assertThat(error.message()).contains("批次管理");
                });
    }

    @Test
    void productTemplateExposesBarcodeAndControlFlags() {
        ImportTemplateRegistry registry = new ImportTemplateRegistry();

        assertThat(registry.headers(ImportConstants.PRODUCT)).containsExactly(
                "product_code", "product_name", "product_type", "category_name", "specification",
                "unit_name", "aux_unit_name", "conversion_factor", "barcode",
                "purchase_price", "sale_price", "tax_rate", "status",
                "lot_controlled", "shelf_life_controlled", "inspection_required", "serial_controlled",
                "remark"
        );
        assertThat(registry.csvTemplate(ImportConstants.PRODUCT))
                .contains("6901234567890")
                .contains("lot_controlled")
                .contains("serial_controlled");
    }

    @Test
    void supplierImportValidatesAndPersistsProfileFields() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        when(supplierMapper.selectCount(any())).thenReturn(0L);
        ImportValidationSupport support = support();
        SupplierImportHandler handler = new SupplierImportHandler(support, supplierMapper);

        ImportTypeHandler.ImportRowPlan plan = handler.validate(1, Map.of(
                "supplier_code", "S002",
                "supplier_name", "profile supplier",
                "email", "supplier@example.com",
                "credit_period", "30"
        ), context());

        assertThat(plan.valid()).isTrue();
        assertThat(plan.normalized())
                .containsEntry("email", "supplier@example.com")
                .containsEntry("creditPeriod", 30);

        handler.commit(new ImportJobEntity(), List.of(row(support, plan)), audit());

        ArgumentCaptor<SupplierEntity> entity = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(supplierMapper).insert(entity.capture());
        assertThat(entity.getValue().getEmail()).isEqualTo("supplier@example.com");
        assertThat(entity.getValue().getCreditPeriod()).isEqualTo(30);
    }

    @Test
    void supplierImportRejectsInvalidCreditPeriod() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        when(supplierMapper.selectCount(any())).thenReturn(0L);
        SupplierImportHandler handler = new SupplierImportHandler(support(), supplierMapper);

        ImportTypeHandler.ImportRowPlan nonNumeric = handler.validate(1, Map.of(
                "supplier_code", "S003",
                "supplier_name", "invalid supplier",
                "credit_period", "thirty"
        ), context());
        ImportTypeHandler.ImportRowPlan negative = handler.validate(2, Map.of(
                "supplier_code", "S004",
                "supplier_name", "negative supplier",
                "credit_period", "-1"
        ), context());

        assertThat(nonNumeric.valid()).isFalse();
        assertThat(nonNumeric.errors())
                .anySatisfy(error -> assertThat(error.column()).isEqualTo("credit_period"));
        assertThat(negative.valid()).isFalse();
        assertThat(negative.errors())
                .anySatisfy(error -> assertThat(error.message()).contains("不能小于0"));
    }

    @Test
    void customerAndSupplierTemplatesExposeProfileFields() {
        ImportTemplateRegistry registry = new ImportTemplateRegistry();

        assertThat(registry.headers(ImportConstants.CUSTOMER)).containsExactly(
                "customer_code", "customer_name", "customer_type", "contact_name", "contact_phone",
                "email", "settlement_method", "credit_limit", "credit_period", "address", "status", "remark"
        );
        assertThat(registry.headers(ImportConstants.SUPPLIER)).containsExactly(
                "supplier_code", "supplier_name", "contact_name", "contact_phone", "email",
                "settlement_method", "credit_period", "address", "status", "remark"
        );
        assertThat(registry.csvTemplate(ImportConstants.CUSTOMER))
                .contains("COMPANY")
                .contains("customer@example.com")
                .contains(",30,");
        assertThat(registry.csvTemplate(ImportConstants.SUPPLIER))
                .contains("supplier@example.com")
                .contains(",30,");
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

        new WarehouseImportHandler(support(), warehouseMapper, deptMapper, userMapper, mock(com.tuowei.erp.masterdata.location.service.LocationService.class))
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
    void warehouseImportCreatesDefaultLocationOnCommit() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        DeptMapper deptMapper = mock(DeptMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        com.tuowei.erp.masterdata.location.service.LocationService locationService =
                mock(com.tuowei.erp.masterdata.location.service.LocationService.class);
        when(warehouseMapper.selectCount(any())).thenReturn(0L);
        ImportValidationSupport support = support();
        WarehouseImportHandler handler = new WarehouseImportHandler(
                support, warehouseMapper, deptMapper, userMapper, locationService
        );

        ImportTypeHandler.ImportRowPlan plan = handler.validate(1, Map.of(
                "warehouse_code", "W-DEF-001",
                "warehouse_name", "default location warehouse"
        ), context());
        assertThat(plan.valid()).isTrue();

        handler.commit(new ImportJobEntity(), List.of(row(support, plan)), audit());

        ArgumentCaptor<WarehouseEntity> warehouse = ArgumentCaptor.forClass(WarehouseEntity.class);
        verify(warehouseMapper).insert(warehouse.capture());
        verify(locationService).ensureDefaultLocation(org.mockito.ArgumentMatchers.eq(warehouse.getValue()), any());
        assertThat(warehouse.getValue().getWarehouseCode()).isEqualTo("W-DEF-001");
    }

    @Test
    void locationImportValidatesAndCreatesViaLocationService() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        LocationMapper locationMapper = mock(LocationMapper.class);
        com.tuowei.erp.masterdata.location.service.LocationService locationService =
                mock(com.tuowei.erp.masterdata.location.service.LocationService.class);
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(9101L);
        warehouse.setWarehouseCode("W001");
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        warehouse.setCompanyId(1L);
        warehouse.setAccountBookId(1L);
        when(warehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(locationMapper.selectCount(any())).thenReturn(0L);
        ImportValidationSupport support = support();
        LocationImportHandler handler = new LocationImportHandler(
                support, warehouseMapper, locationMapper, locationService
        );

        ImportTypeHandler.ImportRowPlan plan = handler.validate(1, Map.of(
                "warehouse_code", "W001",
                "location_code", "a-01",
                "location_name", "A区01",
                "is_default", "1"
        ), context());

        assertThat(plan.valid()).isTrue();
        assertThat(plan.normalized())
                .containsEntry("warehouseId", 9101L)
                .containsEntry("locationCode", "A-01")
                .containsEntry("isDefault", true);

        handler.commit(new ImportJobEntity(), List.of(row(support, plan)), audit());

        ArgumentCaptor<com.tuowei.erp.masterdata.location.web.LocationCreateRequest> request =
                ArgumentCaptor.forClass(com.tuowei.erp.masterdata.location.web.LocationCreateRequest.class);
        verify(locationService).create(request.capture());
        assertThat(request.getValue().warehouseId()).isEqualTo(9101L);
        assertThat(request.getValue().locationCode()).isEqualTo("A-01");
        assertThat(request.getValue().locationName()).isEqualTo("A区01");
        assertThat(request.getValue().isDefault()).isTrue();
    }

    @Test
    void locationTemplateExposesWarehouseAndDefaultFlags() {
        ImportTemplateRegistry registry = new ImportTemplateRegistry();
        assertThat(registry.headers(ImportConstants.LOCATION)).containsExactly(
                "warehouse_code", "location_code", "location_name", "is_default", "status", "remark"
        );
        assertThat(registry.csvTemplate(ImportConstants.LOCATION))
                .contains("W001")
                .contains("A-01");
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
        LocationMapper locationMapper = mock(LocationMapper.class);
        InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
        when(warehouseMapper.selectOne(any())).thenReturn(activeWarehouse());
        when(productMapper.selectOne(any())).thenReturn(activeProduct());
        when(locationMapper.selectOne(any())).thenReturn(activeLocation());
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(null);

        new OpeningInventoryImportHandler(
                support(),
                warehouseMapper,
                productMapper,
                locationMapper,
                inventoryBalanceMapper,
                mock(InventoryTransactionMapper.class),
                mock(InventoryPostingService.class),
                mock(com.tuowei.erp.inventory.serial.service.InventorySerialNumberService.class)
        ).validate(1, Map.of(
                "warehouse_code", "W001",
                "product_code", "P001",
                "location_code", "MAIN",
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

        ArgumentCaptor<LambdaQueryWrapper<LocationEntity>> locationWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(locationMapper).selectOne(locationWrapper.capture());
        assertTenantScoped(locationWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectOne(balanceWrapper.capture());
        assertTenantScoped(balanceWrapper.getValue());
    }

    @Test
    void openingInventoryImportRequiresSerialsForSerialControlledProductsAndRegistersOnCommit() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        LocationMapper locationMapper = mock(LocationMapper.class);
        InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
        InventoryTransactionMapper inventoryTransactionMapper = mock(InventoryTransactionMapper.class);
        InventoryPostingService inventoryPostingService = mock(InventoryPostingService.class);
        com.tuowei.erp.inventory.serial.service.InventorySerialNumberService serialService =
                mock(com.tuowei.erp.inventory.serial.service.InventorySerialNumberService.class);

        ProductEntity serialProduct = activeProduct();
        serialProduct.setSerialControlled(1);
        when(warehouseMapper.selectOne(any())).thenReturn(activeWarehouse());
        when(productMapper.selectOne(any())).thenReturn(serialProduct);
        when(locationMapper.selectOne(any())).thenReturn(activeLocation());
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(null);
        when(inventoryTransactionMapper.selectCount(any())).thenReturn(0L);

        ImportValidationSupport support = support();
        OpeningInventoryImportHandler handler = new OpeningInventoryImportHandler(
                support,
                warehouseMapper,
                productMapper,
                locationMapper,
                inventoryBalanceMapper,
                inventoryTransactionMapper,
                inventoryPostingService,
                serialService
        );

        ImportTypeHandler.ImportRowPlan missing = handler.validate(1, Map.of(
                "warehouse_code", "W001",
                "product_code", "P001",
                "qty_on_hand", "2.0000",
                "amount_on_hand", "20.00",
                "opening_date", "2026-06-08"
        ), context());
        assertThat(missing.valid()).isFalse();
        assertThat(missing.errors())
                .anySatisfy(error -> assertThat(error.column()).isEqualTo("serial_nos"));

        ImportTypeHandler.ImportRowPlan ok = handler.validate(2, Map.of(
                "warehouse_code", "W001",
                "product_code", "P001",
                "qty_on_hand", "2.0000",
                "amount_on_hand", "20.00",
                "opening_date", "2026-06-08",
                "serial_nos", "SN-A,SN-B"
        ), context());
        assertThat(ok.valid()).isTrue();
        assertThat(ok.normalized()).containsEntry("serialNos", "SN-A,SN-B");

        ImportJobRowEntity row = row(support, ok);
        row.setId(99001L);
        ImportJobEntity job = new ImportJobEntity();
        job.setId(88001L);
        handler.commit(job, List.of(row), audit());

        verify(inventoryPostingService).postInbound(any(), any());
        verify(serialService).registerInboundSerials(
                org.mockito.ArgumentMatchers.eq(4401L),
                org.mockito.ArgumentMatchers.eq(4301L),
                org.mockito.ArgumentMatchers.eq(4501L),
                org.mockito.ArgumentMatchers.eq("SN-A,SN-B"),
                org.mockito.ArgumentMatchers.eq("OPENING_BALANCE"),
                org.mockito.ArgumentMatchers.eq("OPEN-INV-88001"),
                org.mockito.ArgumentMatchers.any(),
                any()
        );
    }

    private ImportValidationSupport support() {
        return new ImportValidationSupport(new ObjectMapper());
    }

    private ImportValidationContext context() {
        return new ImportValidationContext(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID);
    }

    private AuditMetadata audit() {
        return new AuditMetadata(
                USER_ID,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );
    }

    private ImportJobRowEntity row(ImportValidationSupport support, ImportTypeHandler.ImportRowPlan plan) {
        ImportJobRowEntity row = new ImportJobRowEntity();
        row.setNormalizedJson(support.toJson(plan.normalized()));
        return row;
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
        entity.setSerialControlled(0);
        return entity;
    }

    private LocationEntity activeLocation() {
        LocationEntity entity = new LocationEntity();
        entity.setId(4501L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setWarehouseId(4301L);
        entity.setLocationCode("MAIN");
        entity.setLocationName("默认库位");
        entity.setIsDefault(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
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
