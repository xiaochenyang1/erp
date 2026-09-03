package com.tuowei.erp.masterdata.supplierproduct.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplierproduct.mapper.SupplierProductRelationMapper;
import com.tuowei.erp.masterdata.supplierproduct.model.SupplierProductRelationEntity;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierProductRelationServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long USER_ID = 303L;
    private static final Long SUPPLIER_ID = 404L;
    private static final Long PRODUCT_ID = 505L;
    private static final Long RELATION_ID = 606L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            USER_ID,
            COMPANY_ID,
            BOOK_ID,
            LocalDateTime.of(2026, 9, 3, 10, 0)
    );

    @Mock private SupplierProductRelationMapper mapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private ProductMapper productMapper;
    @Mock private AuditMetadataFactory audit;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupplierProductRelationEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                SupplierProductRelationEntity.class.getName()
        );
        assistant.setCurrentNamespace(SupplierProductRelationEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SupplierProductRelationEntity.class);
    }

    @Test
    void listMapsProductFieldsForVisibleProduct() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectList(any())).thenReturn(List.of(relation(RELATION_ID, PRODUCT_ID)));

        List<SupplierProductRelationResponse> result = service().list(SUPPLIER_ID);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(RELATION_ID);
            assertThat(response.productId()).isEqualTo(PRODUCT_ID);
            assertThat(response.productCode()).isEqualTo("P-001");
            assertThat(response.productName()).isEqualTo("Product A");
            assertThat(response.minPurchaseQty()).isEqualByComparingTo("5");
            assertThat(response.leadTimeDays()).isEqualTo(7);
            assertThat(response.defaultSupplier()).isTrue();
        });
        ArgumentCaptor<LambdaQueryWrapper<SupplierProductRelationEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id", "account_book_id", "supplier_id", "status", "deleted_flag");
    }

    @Test
    void listKeepsRelationsWhenProductsAreMissingDeletedOrFromAnotherAccountBook() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());

        Long missingProductId = PRODUCT_ID;
        Long deletedProductId = PRODUCT_ID + 1;
        Long foreignProductId = PRODUCT_ID + 2;
        Long inactiveProductId = PRODUCT_ID + 3;
        ProductEntity deleted = activeProduct();
        deleted.setId(deletedProductId);
        deleted.setDeletedFlag(1);
        ProductEntity foreign = activeProduct();
        foreign.setId(foreignProductId);
        foreign.setAccountBookId(BOOK_ID + 1);
        ProductEntity inactive = activeProduct();
        inactive.setId(inactiveProductId);
        inactive.setStatus("INACTIVE");
        when(productMapper.selectById(missingProductId)).thenReturn(null);
        when(productMapper.selectById(deletedProductId)).thenReturn(deleted);
        when(productMapper.selectById(foreignProductId)).thenReturn(foreign);
        when(productMapper.selectById(inactiveProductId)).thenReturn(inactive);
        when(mapper.selectList(any())).thenReturn(List.of(
                relation(RELATION_ID, missingProductId),
                relation(RELATION_ID + 1, deletedProductId),
                relation(RELATION_ID + 2, foreignProductId),
                relation(RELATION_ID + 3, inactiveProductId)
        ));

        List<SupplierProductRelationResponse> result = service().list(SUPPLIER_ID);

        assertThat(result).hasSize(4);
        assertThat(result).allSatisfy(response -> {
            assertThat(response.productId()).isNotNull();
            assertThat(response.productCode()).isNull();
            assertThat(response.productName()).isNull();
        });
    }

    @Test
    void listRejectsSupplierFromAnotherAccountBookBeforeReadingRelations() {
        when(audit.current()).thenReturn(AUDIT);
        SupplierEntity foreign = activeSupplier();
        foreign.setAccountBookId(BOOK_ID + 1);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().list(SUPPLIER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");
        verify(mapper, never()).selectList(any());
    }

    private SupplierProductRelationService service() {
        return new SupplierProductRelationService(mapper, supplierMapper, productMapper, audit);
    }

    private SupplierProductRelationEntity relation(Long id, Long productId) {
        SupplierProductRelationEntity entity = new SupplierProductRelationEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setSupplierId(SUPPLIER_ID);
        entity.setProductId(productId);
        entity.setSupplierProductCode("S-001");
        entity.setMinPurchaseQty(new BigDecimal("5"));
        entity.setLeadTimeDays(7);
        entity.setDefaultSupplierFlag(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private SupplierEntity activeSupplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(SUPPLIER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity activeProduct() {
        ProductEntity entity = new ProductEntity();
        entity.setId(PRODUCT_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setProductCode("P-001");
        entity.setProductName("Product A");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }
}
