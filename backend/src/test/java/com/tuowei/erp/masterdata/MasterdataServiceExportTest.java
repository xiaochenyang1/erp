package com.tuowei.erp.masterdata;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MasterdataServiceExportTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9951L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 18, 13, 30)
    );

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
    void exportProductsWritesScopedCsvRows() throws Exception {
        stubAudit();
        ProductMapper mapper = mock(ProductMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(product()));

        ProductPageQuery query = new ProductPageQuery();
        query.setKeyword("P001");
        query.setStatus("active");
        query.setCategoryName("标准件");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        productService(mapper).exportProducts(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFproductCode,productName,barcode,productType,categoryName,specification,unitName,auxUnitName,conversionFactor,purchasePrice,salePrice,taxRate,status,lotControlled,shelfLifeControlled,inspectionRequired,serialControlled,remark\r\n");
        assertThat(csv).contains("P001,螺栓,6901234567890,STANDARD,标准件,M8,个,箱,12,1.50,2.00,13.00,ACTIVE,true,false,false,false,product export\r\n");
        verifySelectListScoped(mapper, ProductEntity.class);
    }

    @Test
    void exportCustomersWritesScopedCsvRows() throws Exception {
        stubAudit();
        CustomerMapper mapper = mock(CustomerMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(customer()));

        CustomerPageQuery query = new CustomerPageQuery();
        query.setKeyword("C001");
        query.setStatus("active");
        query.setSettlementMethod("MONTHLY");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        customerService(mapper).exportCustomers(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFcustomerCode,customerName,customerType,contactName,contactPhone,email,settlementMethod,creditLimit,creditPeriod,address,status,remark\r\n");
        assertThat(csv).contains("C001,东北客户,COMPANY,老王,13800000001,customer@example.com,MONTHLY,10000.00,,沈阳,ACTIVE,customer export\r\n");
        verifySelectListScoped(mapper, CustomerEntity.class);
    }

    @Test
    void exportSuppliersWritesScopedCsvRows() throws Exception {
        stubAudit();
        SupplierMapper mapper = mock(SupplierMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(supplier()));

        SupplierPageQuery query = new SupplierPageQuery();
        query.setKeyword("S001");
        query.setStatus("active");
        query.setSettlementMethod("MONTHLY");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        supplierService(mapper).exportSuppliers(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFsupplierCode,supplierName,contactName,contactPhone,email,settlementMethod,creditPeriod,address,status,remark\r\n");
        assertThat(csv).contains("S001,钢材供应商,老张,13800000002,supplier@example.com,MONTHLY,30,鞍山,ACTIVE,supplier export\r\n");
        verifySelectListScoped(mapper, SupplierEntity.class);
    }

    @Test
    void exportWarehousesWritesScopedCsvRows() throws Exception {
        stubAudit();
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(warehouse()));

        WarehousePageQuery query = new WarehousePageQuery();
        query.setKeyword("W001");
        query.setStatus("active");
        query.setDeptId(6201L);
        query.setManagerUserId(6301L);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        warehouseService(mapper, mock(DeptMapper.class), mock(UserMapper.class)).exportWarehouses(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFwarehouseCode,warehouseName,deptId,managerUserId,address,status,remark\r\n");
        assertThat(csv).contains("W001,成品仓,6201,6301,沈阳库区,ACTIVE,warehouse export\r\n");
        verifySelectListScoped(mapper, WarehouseEntity.class);
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

    private <T> void verifySelectListScoped(Object mapper, Class<T> entityClass) {
        ArgumentCaptor<LambdaQueryWrapper<T>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        if (mapper instanceof ProductMapper productMapper) {
            verify(productMapper).selectList((LambdaQueryWrapper<ProductEntity>) wrapper.capture());
        } else if (mapper instanceof CustomerMapper customerMapper) {
            verify(customerMapper).selectList((LambdaQueryWrapper<CustomerEntity>) wrapper.capture());
        } else if (mapper instanceof SupplierMapper supplierMapper) {
            verify(supplierMapper).selectList((LambdaQueryWrapper<SupplierEntity>) wrapper.capture());
        } else if (mapper instanceof WarehouseMapper warehouseMapper) {
            verify(warehouseMapper).selectList((LambdaQueryWrapper<WarehouseEntity>) wrapper.capture());
        }
        assertTenantScoped(wrapper.getValue(), entityClass);
    }

    private <T> void assertTenantScoped(LambdaQueryWrapper<T> wrapper, Class<T> entityClass) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    private ProductEntity product() {
        ProductEntity entity = new ProductEntity();
        entity.setId(6101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setProductCode("P001");
        entity.setProductName("螺栓");
        entity.setBarcode("6901234567890");
        entity.setProductType("STANDARD");
        entity.setCategoryName("标准件");
        entity.setSpecification("M8");
        entity.setUnitName("个");
        entity.setAuxUnitName("箱");
        entity.setConversionFactor(new BigDecimal("12"));
        entity.setPurchasePrice(new BigDecimal("1.50"));
        entity.setSalePrice(new BigDecimal("2.00"));
        entity.setTaxRate(new BigDecimal("13.00"));
        entity.setStatus("ACTIVE");
        entity.setLotControlled(1);
        entity.setShelfLifeControlled(0);
        entity.setInspectionRequired(0);
        entity.setSerialControlled(0);
        entity.setRemark("product export");
        entity.setDeletedFlag(0);
        return entity;
    }

    private CustomerEntity customer() {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(6201L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCustomerCode("C001");
        entity.setCustomerName("东北客户");
        entity.setCustomerType("COMPANY");
        entity.setContactName("老王");
        entity.setContactPhone("13800000001");
        entity.setEmail("customer@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditLimit(new BigDecimal("10000.00"));
        entity.setAddress("沈阳");
        entity.setStatus("ACTIVE");
        entity.setRemark("customer export");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SupplierEntity supplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(6301L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setSupplierCode("S001");
        entity.setSupplierName("钢材供应商");
        entity.setContactName("老张");
        entity.setContactPhone("13800000002");
        entity.setEmail("supplier@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditPeriod(30);
        entity.setAddress("鞍山");
        entity.setStatus("ACTIVE");
        entity.setRemark("supplier export");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(6401L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseCode("W001");
        entity.setWarehouseName("成品仓");
        entity.setDeptId(6201L);
        entity.setManagerUserId(6301L);
        entity.setAddress("沈阳库区");
        entity.setStatus("ACTIVE");
        entity.setRemark("warehouse export");
        entity.setDeletedFlag(0);
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
