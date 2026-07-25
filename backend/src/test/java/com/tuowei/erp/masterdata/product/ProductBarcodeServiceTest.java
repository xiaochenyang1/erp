package com.tuowei.erp.masterdata.product;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import com.tuowei.erp.system.dict.service.SystemDictService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBarcodeServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            901L,
            902L,
            903L,
            LocalDateTime.of(2026, 7, 20, 12, 30)
    );

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryBalanceMapper inventoryBalanceMapper;

    @Mock
    private InventoryLotBalanceMapper inventoryLotBalanceMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private SystemDictService systemDictService;

    private ProductService productService;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ProductEntity.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                    new MybatisConfiguration(), ProductEntity.class.getName());
            assistant.setCurrentNamespace(ProductEntity.class.getName());
            TableInfoHelper.initTableInfo(assistant, ProductEntity.class);
        }
    }

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productMapper,
                inventoryBalanceMapper,
                inventoryLotBalanceMapper,
                auditMetadataFactory,
                systemDictService
        );
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createTrimsBarcodeAndReturnsNormalizedValue() {
        when(systemDictService.requireEnabledItem(anyString(), anyString(), anyString()))
                .thenReturn("STANDARD");
        when(productMapper.insert(any(ProductEntity.class))).thenReturn(1);

        ProductResponse response = productService.create(createRequest(" 6901234567890 "));

        ArgumentCaptor<ProductEntity> entity = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insert(entity.capture());
        assertThat(entity.getValue().getBarcode()).isEqualTo("6901234567890");
        assertThat(response.barcode()).isEqualTo("6901234567890");
    }

    @Test
    void createStoresBlankBarcodeAsNullWithoutDuplicateLookup() {
        when(systemDictService.requireEnabledItem(anyString(), anyString(), anyString()))
                .thenReturn("STANDARD");
        when(productMapper.insert(any(ProductEntity.class))).thenReturn(1);

        productService.create(createRequest("   "));

        ArgumentCaptor<ProductEntity> entity = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insert(entity.capture());
        verify(productMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        assertThat(entity.getValue().getBarcode()).isNull();
    }

    @Test
    void lookupByBarcodeUsesCompanyAccountBookActiveAndDeletedScopes() {
        ProductEntity stored = product(904L, AUDIT.accountBookId(), "6901234567890");
        when(productMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

        ProductResponse response = productService.getByBarcode(" 6901234567890 ");

        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectOne(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("status")
                .contains("barcode");
        assertThat(wrapper.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", "6901234567890");
        assertThat(response.id()).isEqualTo(904L);
        assertThat(response.barcode()).isEqualTo("6901234567890");
    }

    @Test
    void createRejectsBarcodeAlreadyUsedInCurrentAccountBook() {
        when(productMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(product(904L, AUDIT.accountBookId(), "6901234567890"));

        assertThatThrownBy(() -> productService.create(createRequest("6901234567890")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品条码已存在");
        verify(productMapper, never()).insert(any(ProductEntity.class));
    }

    @Test
    void updateRejectsBarcodeUsedByAnotherProduct() {
        ProductEntity current = product(904L, AUDIT.accountBookId(), "OLD-CODE");
        when(productMapper.selectById(904L)).thenReturn(current);
        when(productMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(product(905L, AUDIT.accountBookId(), "6901234567890"));

        assertThatThrownBy(() -> productService.update(904L, updateRequest("6901234567890")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品条码已存在");
        verify(productMapper, never()).updateById(any(ProductEntity.class));
    }

    private ProductCreateRequest createRequest(String barcode) {
        return new ProductCreateRequest(
                "BARCODE-P001",
                "条码商品",
                "STANDARD",
                "条码测试",
                "规格",
                "件",
                new BigDecimal("10.00"
        ),
                new BigDecimal("12.00"),
                new BigDecimal("13.0000"),
                false, false, false, false, "barcode test", barcode
        );
    }

    private ProductUpdateRequest updateRequest(String barcode) {
        return new ProductUpdateRequest(
                "条码商品",
                "条码测试",
                "规格",
                "件",
                new BigDecimal("10.00"
        ),
                new BigDecimal("12.00"),
                new BigDecimal("13.0000"),
                false, false, false, false, "barcode test", barcode
        );
    }

    private ProductEntity product(Long id, Long accountBookId, String barcode) {
        ProductEntity entity = new ProductEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setProductCode("BARCODE-P" + id);
        entity.setProductName("条码商品");
        entity.setProductType("STANDARD");
        entity.setCategoryName("条码测试");
        entity.setUnitName("件");
        entity.setPurchasePrice(new BigDecimal("10.00"));
        entity.setSalePrice(new BigDecimal("12.00"));
        entity.setTaxRate(new BigDecimal("13.0000"));
        entity.setBarcode(barcode);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setLotControlled(0);
        entity.setShelfLifeControlled(0);
        entity.setInspectionRequired(0);
        return entity;
    }
}
