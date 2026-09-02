package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
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
@SuppressWarnings({"unchecked", "rawtypes"})
class ProductCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 15, 0)
    );
    private static final Long PRODUCT_ID = 101L;

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
    @Mock
    private ProductQueryService productQueryService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryLotBalanceEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantProductNormalizesBarcodeAuxUnitAndAuditFields() {
        when(systemDictService.requireEnabledItem(anyString(), anyString(), anyString())).thenReturn("STANDARD");
        when(productMapper.insert(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(PRODUCT_ID);
            return 1;
        });
        ProductResponse expected = response("ACTIVE");
        when(productQueryService.toResponse(any(ProductEntity.class))).thenReturn(expected);

        ProductResponse actual = service().create(createRequest(false, false, " 6901234567890 ", " 箱 ", new BigDecimal("12.00")));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<ProductEntity> entityCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insert(entityCaptor.capture());
        ProductEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getProductType()).isEqualTo("STANDARD");
        assertThat(inserted.getBarcode()).isEqualTo("6901234567890");
        assertThat(inserted.getAuxUnitName()).isEqualTo("箱");
        assertThat(inserted.getConversionFactor()).isEqualByComparingTo("12");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getLotControlled()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();

        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> barcodeQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectOne(barcodeQueryCaptor.capture());
        assertThat(barcodeQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "barcode", "limit 1");
        assertThat(barcodeQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "6901234567890");
    }

    @Test
    void createRejectsShelfLifeControlWithoutLotControlBeforeWriting() {
        assertThatThrownBy(() -> service().create(createRequest(false, true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("启用效期管理必须同时启用批次管理");

        verify(productMapper, never()).insert(any(ProductEntity.class));
        verify(systemDictService, never()).requireEnabledItem(anyString(), anyString(), anyString());
    }

    @Test
    void updateRejectsEnablingLotControlWhenAggregateStockExists() {
        ProductEntity existing = product(0);
        when(productQueryService.requireProduct(PRODUCT_ID)).thenReturn(existing);
        when(inventoryBalanceMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().update(PRODUCT_ID, updateRequest(true, false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品已有库存，不能直接启用批次管理");

        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).exists(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "product_id", "qty_on_hand", "qty_reserved", "amount_on_hand");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), PRODUCT_ID);
        verify(productMapper, never()).updateById(any(ProductEntity.class));
    }

    @Test
    void updateRejectsDisablingLotControlWhenLotStockExists() {
        ProductEntity existing = product(1);
        when(productQueryService.requireProduct(PRODUCT_ID)).thenReturn(existing);
        when(inventoryLotBalanceMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().update(PRODUCT_ID, updateRequest(false, false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品存在批次库存，不能关闭批次管理");

        verify(productMapper, never()).updateById(any(ProductEntity.class));
    }

    @Test
    void disableUpdatesStatusAndSurfacesOptimisticConflict() {
        ProductEntity existing = product(0);
        when(productQueryService.requireProduct(PRODUCT_ID)).thenReturn(existing);
        when(productMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(PRODUCT_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("商品已被其他操作修改，请刷新后重试");

        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(productQueryService, never()).toResponse(any(ProductEntity.class));
    }

    private ProductCommandService service() {
        return new ProductCommandService(
                productMapper,
                inventoryBalanceMapper,
                inventoryLotBalanceMapper,
                auditMetadataFactory,
                systemDictService,
                productQueryService
        );
    }

    private ProductCreateRequest createRequest(
            boolean lotControlled,
            boolean shelfLifeControlled,
            String barcode,
            String auxUnitName,
            BigDecimal conversionFactor
    ) {
        return new ProductCreateRequest(
                "P-001",
                "螺栓",
                "STANDARD",
                "标准件",
                "M8",
                "个",
                auxUnitName,
                conversionFactor,
                new BigDecimal("1.50"),
                new BigDecimal("2.00"),
                new BigDecimal("13.00"),
                lotControlled,
                shelfLifeControlled,
                false,
                false,
                "command test",
                barcode
        );
    }

    private ProductUpdateRequest updateRequest(boolean lotControlled, boolean shelfLifeControlled, String barcode) {
        return new ProductUpdateRequest(
                "螺栓更新",
                "标准件",
                "M10",
                "个",
                null,
                null,
                new BigDecimal("1.80"),
                new BigDecimal("2.30"),
                new BigDecimal("13.00"),
                lotControlled,
                shelfLifeControlled,
                false,
                false,
                "updated",
                barcode
        );
    }

    private ProductEntity product(int lotControlled) {
        ProductEntity entity = new ProductEntity();
        entity.setId(PRODUCT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setProductCode("P-001");
        entity.setProductName("螺栓");
        entity.setProductType("STANDARD");
        entity.setCategoryName("标准件");
        entity.setUnitName("个");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setLotControlled(lotControlled);
        entity.setShelfLifeControlled(0);
        entity.setInspectionRequired(0);
        entity.setSerialControlled(0);
        entity.setVersion(0);
        return entity;
    }

    private ProductResponse response(String status) {
        return new ProductResponse(
                PRODUCT_ID,
                "P-001",
                "螺栓",
                "STANDARD",
                "标准件",
                "M8",
                "个",
                "箱",
                new BigDecimal("12"),
                new BigDecimal("1.50"),
                new BigDecimal("2.00"),
                new BigDecimal("13.00"),
                status,
                false,
                false,
                false,
                false,
                "command test",
                "6901234567890"
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityClass.getName()
        );
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
