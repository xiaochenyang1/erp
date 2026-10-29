package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryTransactionWriter;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryPostingServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final Long WAREHOUSE_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;

    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
    private final InventoryTransactionMapper inventoryTransactionMapper = mock(InventoryTransactionMapper.class);
    private final InventoryReservationPostingService inventoryReservationPostingService = mock(InventoryReservationPostingService.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper = mock(InventoryLotBalanceMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @Test
    void postInboundScopesProductPostedTransactionAndBalanceLookupsByCompanyAndAccountBook() {
        when(productMapper.selectOne(any())).thenReturn(activeProduct());
        when(inventoryTransactionMapper.selectOne(any())).thenReturn(null);
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(null);
        when(inventoryBalanceMapper.insert(any(InventoryBalanceEntity.class))).thenReturn(1);
        when(inventoryTransactionMapper.insert(any(InventoryTransactionEntity.class))).thenReturn(1);

        service().postInbound(command(), AUDIT);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> productWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectOne(productWrapperCaptor.capture());
        assertTenantScoped(productWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> transactionWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper).selectOne(transactionWrapperCaptor.capture());
        assertTenantScoped(transactionWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectOne(balanceWrapperCaptor.capture());
        assertTenantScoped(balanceWrapperCaptor.getValue());
    }

    @Test
    void serviceDoesNotKeepLegacyCompanyOnlyBalanceLookup() throws Exception {
        String querySource = serviceSource("InventoryPostingQueryService.java");
        String postingSource = serviceSource("InventoryBalancePostingService.java");

        assertThat(querySource)
                .contains("Long companyId, Long accountBookId, Long warehouseId, Long productId, Long locationId")
                .doesNotContain("private InventoryBalanceEntity selectBalance(Long companyId, Long warehouseId, Long productId)");
        assertThat(postingSource)
                .contains("Long companyId, Long accountBookId, Long warehouseId, Long productId, Long locationId")
                .doesNotContain("private InventoryBalanceEntity selectBalance(Long companyId, Long warehouseId, Long productId)");
    }

    private InventoryPostingService service() {
        return new InventoryPostingService(
                inventoryBalanceMapper,
                new InventoryTransactionWriter(inventoryTransactionMapper),
                inventoryReservationPostingService,
                productMapper,
                inventoryLotBalanceMapper
        );
    }

    private String serviceSource(String fileName) throws Exception {
        return Files.readString(
                Path.of("src", "main", "java", "com", "tuowei", "erp", "inventory", "stock", "service", fileName),
                StandardCharsets.UTF_8
        );
    }

    private InventoryPostingCommand command() {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                PRODUCT_ID,
                "TENANT_POSTING",
                "TP-9931",
                8001L,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "tenant boundary"
        );
    }

    private ProductEntity activeProduct() {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(AUDIT.accountBookId());
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        product.setLotControlled(0);
        product.setShelfLifeControlled(0);
        return product;
    }

    private void assertTenantScoped(AbstractWrapper<?, ?, ?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
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
