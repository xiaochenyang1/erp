package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
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
class ProductQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 14, 30)
    );

    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ProductEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                ProductEntity.class.getName()
        );
        assistant.setCurrentNamespace(ProductEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProductEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationScopesTenantAndMapsFlags() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(productMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ProductEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(product(AUDIT.accountBookId())));
            return page;
        });
        ProductPageQuery query = new ProductPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  P-001  ");
        query.setStatus(" active ");
        query.setCategoryName(" 标准件 ");

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.productCode()).isEqualTo("P-001");
            assertThat(record.lotControlled()).isTrue();
            assertThat(record.shelfLifeControlled()).isFalse();
            assertThat(record.serialControlled()).isTrue();
        });

        ArgumentCaptor<Page<ProductEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("product_code")
                .contains("product_name")
                .contains("barcode")
                .contains("status")
                .contains("category_name")
                .contains("order by product_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%P-001%", "ACTIVE", "标准件");
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(productMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<ProductEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(productMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void getByBarcodeTrimsValueAndRequiresActiveTenantRecord() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(productMapper.selectOne(any())).thenReturn(product(AUDIT.accountBookId()));

        var response = service().getByBarcode("  6901234567890  ");

        assertThat(response.barcode()).isEqualTo("6901234567890");
        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "status", "barcode", "limit 1");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", "6901234567890");
    }

    @Test
    void getByIdRejectsProductFromAnotherAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(productMapper.selectById(101L)).thenReturn(product(9999L));

        assertThatThrownBy(() -> service().getById(101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在");
    }

    private ProductQueryService service() {
        return new ProductQueryService(productMapper, auditMetadataFactory);
    }

    private ProductEntity product(Long accountBookId) {
        ProductEntity entity = new ProductEntity();
        entity.setId(101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setProductCode("P-001");
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
        entity.setDeletedFlag(0);
        entity.setLotControlled(1);
        entity.setShelfLifeControlled(0);
        entity.setInspectionRequired(0);
        entity.setSerialControlled(1);
        entity.setRemark("query test");
        return entity;
    }
}
