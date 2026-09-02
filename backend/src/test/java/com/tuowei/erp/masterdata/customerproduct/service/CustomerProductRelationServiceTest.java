package com.tuowei.erp.masterdata.customerproduct.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customerproduct.mapper.CustomerProductRelationMapper;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationRequest;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CustomerProductRelationServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long CUSTOMER_ID = 301L;
    private static final Long PRODUCT_ID = 401L;
    private static final Long RELATION_ID = 501L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 10, 15);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, BOOK_ID, NOW);

    @Mock private CustomerProductRelationMapper mapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private ProductMapper productMapper;
    @Mock private AuditMetadataFactory audit;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(CustomerProductRelationEntity.class);
    }

    @Test
    void listFiltersTenantActiveRowsAndMapsProductNames() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectList(any())).thenReturn(List.of(existingRelation()));

        List<CustomerProductRelationResponse> result = service().list(CUSTOMER_ID);

        assertThat(result).hasSize(1);
        CustomerProductRelationResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(RELATION_ID);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.productCode()).isEqualTo("P-001");
        assertThat(response.productName()).isEqualTo("成品A");
        assertThat(response.customerProductCode()).isEqualTo("C-001");
        assertThat(response.status()).isEqualTo("ACTIVE");

        String sql = capturedListSql();
        assertThat(sql).contains("company_id").contains("account_book_id")
                .contains("customer_id").contains("status").contains("deleted_flag");
    }

    @Test
    void listKeepsRelationWhenProductNoLongerVisible() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of(existingRelation()));

        List<CustomerProductRelationResponse> result = service().list(CUSTOMER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.get(0).productCode()).isNull();
        assertThat(result.get(0).productName()).isNull();
    }

    @Test
    void listRejectsCustomerFromAnotherTenant() {
        when(audit.current()).thenReturn(AUDIT);
        CustomerEntity foreign = activeCustomer();
        foreign.setCompanyId(999L);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().list(CUSTOMER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");
        verify(mapper, never()).selectList(any());
    }

    @Test
    void saveInsertsNewRelationWithTenantAuditAndTrimmedText() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectOne(any())).thenReturn(null);

        CustomerProductRelationResponse result = service().save(CUSTOMER_ID, new CustomerProductRelationRequest(
                PRODUCT_ID, "  C-001 ", "  客户料号A ", " 每周二送货 ", " 纸箱 ", "  备注 "));

        ArgumentCaptor<CustomerProductRelationEntity> captor =
                ArgumentCaptor.forClass(CustomerProductRelationEntity.class);
        verify(mapper).insert(captor.capture());
        CustomerProductRelationEntity entity = captor.getValue();
        assertThat(entity.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(entity.getAccountBookId()).isEqualTo(BOOK_ID);
        assertThat(entity.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(entity.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(entity.getCustomerProductCode()).isEqualTo("C-001");
        assertThat(entity.getCustomerProductName()).isEqualTo("客户料号A");
        assertThat(entity.getDeliveryPreference()).isEqualTo("每周二送货");
        assertThat(entity.getPackagingPreference()).isEqualTo("纸箱");
        assertThat(entity.getRemark()).isEqualTo("备注");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getDeletedFlag()).isZero();
        assertThat(entity.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(entity.getCreatedTime()).isEqualTo(NOW);
        assertThat(entity.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(entity.getVersion()).isZero();
        verify(mapper, never()).updateById(any(CustomerProductRelationEntity.class));
        assertThat(result.productCode()).isEqualTo("P-001");
    }

    @Test
    void saveUpdatesExistingRelationAndRevivesSoftDeletedRow() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        CustomerProductRelationEntity existing = existingRelation();
        existing.setDeletedFlag(1);
        existing.setStatus("INACTIVE");
        when(mapper.selectOne(any())).thenReturn(existing);

        service().save(CUSTOMER_ID, new CustomerProductRelationRequest(
                PRODUCT_ID, "C-002", null, null, null, null));

        verify(mapper, never()).insert(any(CustomerProductRelationEntity.class));
        ArgumentCaptor<CustomerProductRelationEntity> captor =
                ArgumentCaptor.forClass(CustomerProductRelationEntity.class);
        verify(mapper).updateById(captor.capture());
        CustomerProductRelationEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(RELATION_ID);
        assertThat(entity.getDeletedFlag()).isZero();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCustomerProductCode()).isEqualTo("C-002");
        assertThat(entity.getCustomerProductName()).isNull();
        assertThat(entity.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(entity.getUpdatedTime()).isEqualTo(NOW);
    }

    @Test
    void saveRejectsProductFromAnotherTenant() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        ProductEntity foreign = activeProduct();
        foreign.setAccountBookId(999L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().save(CUSTOMER_ID, new CustomerProductRelationRequest(
                PRODUCT_ID, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在");
        verify(mapper, never()).insert(any(CustomerProductRelationEntity.class));
        verify(mapper, never()).updateById(any(CustomerProductRelationEntity.class));
    }

    @Test
    void deleteSoftDeletesRelation() {
        when(audit.current()).thenReturn(AUDIT);
        when(mapper.selectById(RELATION_ID)).thenReturn(existingRelation());

        service().delete(CUSTOMER_ID, RELATION_ID);

        ArgumentCaptor<CustomerProductRelationEntity> captor =
                ArgumentCaptor.forClass(CustomerProductRelationEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeletedFlag()).isEqualTo(1);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getUpdatedTime()).isEqualTo(NOW);
    }

    @Test
    void deleteRejectsRelationBelongingToAnotherCustomer() {
        when(audit.current()).thenReturn(AUDIT);
        CustomerProductRelationEntity other = existingRelation();
        other.setCustomerId(777L);
        when(mapper.selectById(RELATION_ID)).thenReturn(other);

        assertThatThrownBy(() -> service().delete(CUSTOMER_ID, RELATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户商品关系不存在");
        verify(mapper, never()).updateById(any(CustomerProductRelationEntity.class));
    }

    @Test
    void findScopesLookupToActiveTenantRow() {
        when(mapper.selectOne(any())).thenReturn(existingRelation());

        assertThat(service().find(CUSTOMER_ID, PRODUCT_ID, AUDIT)).isNotNull();

        ArgumentCaptor<LambdaQueryWrapper<CustomerProductRelationEntity>> captor = queryCaptor();
        verify(mapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id").contains("account_book_id")
                .contains("customer_id").contains("product_id")
                .contains("status").contains("deleted_flag");
    }

    private String capturedListSql() {
        ArgumentCaptor<LambdaQueryWrapper<CustomerProductRelationEntity>> captor = queryCaptor();
        verify(mapper).selectList(captor.capture());
        return captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<CustomerProductRelationEntity>> queryCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private CustomerProductRelationService service() {
        return new CustomerProductRelationService(mapper, customerMapper, productMapper, audit);
    }

    private CustomerProductRelationEntity existingRelation() {
        CustomerProductRelationEntity entity = new CustomerProductRelationEntity();
        entity.setId(RELATION_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setProductId(PRODUCT_ID);
        entity.setCustomerProductCode("C-001");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private CustomerEntity activeCustomer() {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(CUSTOMER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setCustomerName("Acme");
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
