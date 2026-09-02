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
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationRequest;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierProductRelationServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long SUPPLIER_ID = 301L;
    private static final Long PRODUCT_ID = 401L;
    private static final Long RELATION_ID = 501L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 10, 15);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, BOOK_ID, NOW);

    @Mock private SupplierProductRelationMapper mapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private ProductMapper productMapper;
    @Mock private AuditMetadataFactory audit;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SupplierProductRelationEntity.class);
    }

    @Test
    void listOrdersDefaultSupplierFirstAndMapsProductNames() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectList(any())).thenReturn(List.of(existingRelation()));

        List<SupplierProductRelationResponse> result = service().list(SUPPLIER_ID);

        assertThat(result).hasSize(1);
        SupplierProductRelationResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(RELATION_ID);
        assertThat(response.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(response.productCode()).isEqualTo("P-001");
        assertThat(response.productName()).isEqualTo("成品A");
        assertThat(response.minPurchaseQty()).isEqualByComparingTo("5");
        assertThat(response.leadTimeDays()).isEqualTo(7);
        assertThat(response.defaultSupplier()).isTrue();

        String sql = capturedListSql();
        assertThat(sql).contains("company_id").contains("account_book_id")
                .contains("supplier_id").contains("status").contains("deleted_flag");
    }

    @Test
    void listKeepsRelationWhenProductNoLongerVisible() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of(existingRelation()));

        List<SupplierProductRelationResponse> result = service().list(SUPPLIER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.get(0).productCode()).isNull();
        assertThat(result.get(0).productName()).isNull();
    }

    @Test
    void listRejectsSupplierFromAnotherTenant() {
        when(audit.current()).thenReturn(AUDIT);
        SupplierEntity foreign = activeSupplier();
        foreign.setCompanyId(999L);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().list(SUPPLIER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");
        verify(mapper, never()).selectList(any());
    }

    @Test
    void saveInsertsNewRelationWithDefaultsForBlankNumbers() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectOne(any())).thenReturn(null);

        SupplierProductRelationResponse result = service().save(SUPPLIER_ID, new SupplierProductRelationRequest(
                PRODUCT_ID, "  S-001 ", " 供应商料号A ", null, null, null, "  备注 "));

        ArgumentCaptor<SupplierProductRelationEntity> captor =
                ArgumentCaptor.forClass(SupplierProductRelationEntity.class);
        verify(mapper).insert(captor.capture());
        SupplierProductRelationEntity entity = captor.getValue();
        assertThat(entity.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(entity.getAccountBookId()).isEqualTo(BOOK_ID);
        assertThat(entity.getSupplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(entity.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(entity.getSupplierProductCode()).isEqualTo("S-001");
        assertThat(entity.getSupplierProductName()).isEqualTo("供应商料号A");
        assertThat(entity.getMinPurchaseQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entity.getLeadTimeDays()).isZero();
        assertThat(entity.getDefaultSupplierFlag()).isZero();
        assertThat(entity.getRemark()).isEqualTo("备注");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getDeletedFlag()).isZero();
        assertThat(entity.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(entity.getCreatedTime()).isEqualTo(NOW);
        assertThat(entity.getVersion()).isZero();
        assertThat(result.productCode()).isEqualTo("P-001");
    }

    @Test
    void saveClearsOtherDefaultSuppliersForSameProduct() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        SupplierProductRelationEntity current = existingRelation();
        when(mapper.selectOne(any())).thenReturn(current);
        SupplierProductRelationEntity otherDefault = existingRelation();
        otherDefault.setId(902L);
        otherDefault.setSupplierId(902L);
        SupplierProductRelationEntity self = existingRelation();
        when(mapper.selectList(any())).thenReturn(List.of(otherDefault, self));

        service().save(SUPPLIER_ID, new SupplierProductRelationRequest(
                PRODUCT_ID, "S-001", null, new BigDecimal("10"), 3, true, null));

        ArgumentCaptor<SupplierProductRelationEntity> captor =
                ArgumentCaptor.forClass(SupplierProductRelationEntity.class);
        verify(mapper, times(2)).updateById(captor.capture());
        List<SupplierProductRelationEntity> updates = captor.getAllValues();
        assertThat(updates.get(0).getId()).isEqualTo(902L);
        assertThat(updates.get(0).getDefaultSupplierFlag()).isZero();
        assertThat(updates.get(0).getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(updates.get(1).getId()).isEqualTo(RELATION_ID);
        assertThat(updates.get(1).getDefaultSupplierFlag()).isEqualTo(1);
        assertThat(updates.get(1).getMinPurchaseQty()).isEqualByComparingTo("10");
        assertThat(updates.get(1).getLeadTimeDays()).isEqualTo(3);
        verify(mapper, never()).insert(any(SupplierProductRelationEntity.class));
    }

    @Test
    void saveRevivesSoftDeletedRelation() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        SupplierProductRelationEntity existing = existingRelation();
        existing.setDeletedFlag(1);
        existing.setStatus("INACTIVE");
        when(mapper.selectOne(any())).thenReturn(existing);

        service().save(SUPPLIER_ID, new SupplierProductRelationRequest(
                PRODUCT_ID, "S-002", null, null, null, false, null));

        ArgumentCaptor<SupplierProductRelationEntity> captor =
                ArgumentCaptor.forClass(SupplierProductRelationEntity.class);
        verify(mapper).updateById(captor.capture());
        SupplierProductRelationEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(RELATION_ID);
        assertThat(entity.getDeletedFlag()).isZero();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getDefaultSupplierFlag()).isZero();
        assertThat(entity.getUpdatedTime()).isEqualTo(NOW);
        verify(mapper, never()).insert(any(SupplierProductRelationEntity.class));
    }

    @Test
    void saveRejectsProductFromAnotherTenant() {
        when(audit.current()).thenReturn(AUDIT);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        ProductEntity foreign = activeProduct();
        foreign.setAccountBookId(999L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().save(SUPPLIER_ID, new SupplierProductRelationRequest(
                PRODUCT_ID, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在");
        verify(mapper, never()).insert(any(SupplierProductRelationEntity.class));
        verify(mapper, never()).updateById(any(SupplierProductRelationEntity.class));
    }

    @Test
    void deleteSoftDeletesRelation() {
        when(audit.current()).thenReturn(AUDIT);
        when(mapper.selectById(RELATION_ID)).thenReturn(existingRelation());

        service().delete(SUPPLIER_ID, RELATION_ID);

        ArgumentCaptor<SupplierProductRelationEntity> captor =
                ArgumentCaptor.forClass(SupplierProductRelationEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeletedFlag()).isEqualTo(1);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getUpdatedTime()).isEqualTo(NOW);
    }

    @Test
    void deleteRejectsRelationBelongingToAnotherSupplier() {
        when(audit.current()).thenReturn(AUDIT);
        SupplierProductRelationEntity other = existingRelation();
        other.setSupplierId(777L);
        when(mapper.selectById(RELATION_ID)).thenReturn(other);

        assertThatThrownBy(() -> service().delete(SUPPLIER_ID, RELATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商商品关系不存在");
        verify(mapper, never()).updateById(any(SupplierProductRelationEntity.class));
    }

    @Test
    void findScopesLookupToActiveTenantRow() {
        when(mapper.selectOne(any())).thenReturn(existingRelation());

        assertThat(service().find(SUPPLIER_ID, PRODUCT_ID, AUDIT)).isNotNull();

        ArgumentCaptor<LambdaQueryWrapper<SupplierProductRelationEntity>> captor = queryCaptor();
        verify(mapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id").contains("account_book_id")
                .contains("supplier_id").contains("product_id")
                .contains("status").contains("deleted_flag");
    }

    private String capturedListSql() {
        ArgumentCaptor<LambdaQueryWrapper<SupplierProductRelationEntity>> captor = queryCaptor();
        verify(mapper).selectList(captor.capture());
        return captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<SupplierProductRelationEntity>> queryCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private SupplierProductRelationService service() {
        return new SupplierProductRelationService(mapper, supplierMapper, productMapper, audit);
    }

    private SupplierProductRelationEntity existingRelation() {
        SupplierProductRelationEntity entity = new SupplierProductRelationEntity();
        entity.setId(RELATION_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setSupplierId(SUPPLIER_ID);
        entity.setProductId(PRODUCT_ID);
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
        entity.setSupplierName("Acme Supply");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity activeProduct() {
        ProductEntity entity = new ProductEntity();
        entity.setId(PRODUCT_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setProductCode("P-001");
        entity.setProductName("成品A");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
