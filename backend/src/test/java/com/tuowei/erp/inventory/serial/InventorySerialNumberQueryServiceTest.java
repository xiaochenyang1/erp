package com.tuowei.erp.inventory.serial;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.model.InventorySerialNumberEntity;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberQueryService;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventorySerialNumberQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7101L, 8101L, 9101L, LocalDateTime.of(2026, 8, 28, 10, 0)
    );

    private final InventorySerialNumberMapper serialNumberMapper = mock(InventorySerialNumberMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventorySerialNumberEntity.class);
        initTableInfo(ProductEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesTenant() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventorySerialNumberEntity serial = serial();
        ProductEntity product = product(AUDIT.accountBookId());
        when(serialNumberMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<InventorySerialNumberEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(serial));
            page.setTotal(1L);
            return page;
        });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));

        InventorySerialPageQuery query = new InventorySerialPageQuery();
        query.setPageNo(0L);
        query.setPageSize(999L);
        query.setProductId(7001L);
        query.setWarehouseId(6001L);
        query.setLocationId(5001L);
        query.setStatus(" in_stock ");
        query.setKeyword(" SN-001 ");

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.serialNo()).isEqualTo("SN-001");
            assertThat(response.productCode()).isEqualTo("P-001");
            assertThat(response.productName()).isEqualTo("商品一");
        });

        ArgumentCaptor<Page<InventorySerialNumberEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<InventorySerialNumberEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(serialNumberMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("product_id")
                .contains("warehouse_id")
                .contains("location_id")
                .contains("status")
                .contains("serial_no")
                .contains("inbound_biz_no")
                .contains("outbound_biz_no");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 7001L, 6001L, 5001L, "IN_STOCK");
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(serialNumberMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<InventorySerialNumberEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(serialNumberMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void listDropsProductHydrationFromAnotherAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(serialNumberMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<InventorySerialNumberEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(serial()));
            page.setTotal(1L);
            return page;
        });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product(9999L)));

        var result = service().list(null);

        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.productId()).isEqualTo(7001L);
            assertThat(response.productCode()).isNull();
            assertThat(response.productName()).isNull();
        });
    }

    private InventorySerialNumberQueryService service() {
        return new InventorySerialNumberQueryService(serialNumberMapper, productMapper, auditMetadataFactory);
    }

    private InventorySerialNumberEntity serial() {
        InventorySerialNumberEntity entity = new InventorySerialNumberEntity();
        entity.setId(8001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setProductId(7001L);
        entity.setWarehouseId(6001L);
        entity.setLocationId(5001L);
        entity.setSerialNo("SN-001");
        entity.setStatus("IN_STOCK");
        entity.setUpdatedTime(AUDIT.now());
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity product(Long accountBookId) {
        ProductEntity entity = new ProductEntity();
        entity.setId(7001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setProductCode("P-001");
        entity.setProductName("商品一");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), type.getName()
        );
        assistant.setCurrentNamespace(type.getName());
        TableInfoHelper.initTableInfo(assistant, type);
    }
}
