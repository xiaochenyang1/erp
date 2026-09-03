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
    private static final Long USER_ID = 303L;
    private static final Long CUSTOMER_ID = 404L;
    private static final Long PRODUCT_ID = 505L;
    private static final Long RELATION_ID = 606L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            USER_ID,
            COMPANY_ID,
            BOOK_ID,
            LocalDateTime.of(2026, 9, 3, 10, 0)
    );

    @Mock private CustomerProductRelationMapper mapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private ProductMapper productMapper;
    @Mock private AuditMetadataFactory audit;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(CustomerProductRelationEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                CustomerProductRelationEntity.class.getName()
        );
        assistant.setCurrentNamespace(CustomerProductRelationEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, CustomerProductRelationEntity.class);
    }

    @Test
    void listMapsProductFieldsForVisibleProduct() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(mapper.selectList(any())).thenReturn(List.of(relation(RELATION_ID, PRODUCT_ID)));

        List<CustomerProductRelationResponse> result = service().list(CUSTOMER_ID);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(RELATION_ID);
            assertThat(response.productId()).isEqualTo(PRODUCT_ID);
            assertThat(response.productCode()).isEqualTo("P-001");
            assertThat(response.productName()).isEqualTo("Product A");
        });
        ArgumentCaptor<LambdaQueryWrapper<CustomerProductRelationEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id", "account_book_id", "customer_id", "status", "deleted_flag");
    }

    @Test
    void listKeepsRelationsWhenProductsAreMissingDeletedOrFromAnotherAccountBook() {
        when(audit.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());

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

        List<CustomerProductRelationResponse> result = service().list(CUSTOMER_ID);

        assertThat(result).hasSize(4);
        assertThat(result).allSatisfy(response -> {
            assertThat(response.productId()).isNotNull();
            assertThat(response.productCode()).isNull();
            assertThat(response.productName()).isNull();
        });
    }

    @Test
    void listRejectsCustomerFromAnotherAccountBookBeforeReadingRelations() {
        when(audit.current()).thenReturn(AUDIT);
        CustomerEntity foreign = activeCustomer();
        foreign.setAccountBookId(BOOK_ID + 1);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().list(CUSTOMER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");
        verify(mapper, never()).selectList(any());
    }

    private CustomerProductRelationService service() {
        return new CustomerProductRelationService(mapper, customerMapper, productMapper, audit);
    }

    private CustomerProductRelationEntity relation(Long id, Long productId) {
        CustomerProductRelationEntity entity = new CustomerProductRelationEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setProductId(productId);
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
