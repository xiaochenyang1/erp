package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryLotPostingServiceTest {

    private static final long COMPANY_ID = 9711L;
    private static final long ACCOUNT_BOOK_ID = 9722L;
    private static final long USER_ID = 9733L;
    private static final long WAREHOUSE_ID = 9744L;
    private static final long PRODUCT_ID = 9755L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 11, 0);

    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper = mock(InventoryLotBalanceMapper.class);
    private final InventoryTransactionWriter inventoryTransactionWriter = mock(InventoryTransactionWriter.class);
    private final InventoryLotPostingService service = new InventoryLotPostingService(
            inventoryBalanceMapper,
            inventoryLotBalanceMapper,
            inventoryTransactionWriter
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryLotBalanceEntity.class);
    }

    @Test
    void keepsLotValidationRulesInsideExtractedCollaborator() {
        assertThatThrownBy(() -> service.validateInboundCommand(
                product(0, 0),
                command("LOT-A", null, null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未启用批次管理的商品不能填写批次信息");

        assertThatThrownBy(() -> service.validateInboundCommand(
                product(1, 1),
                command("LOT-A", null, null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("启用效期管理的商品必须填写有效期");

        assertThatThrownBy(() -> service.validateOutboundCommand(
                product(1, 0),
                command(null, LocalDate.of(2026, 7, 1), null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("出库填写批次生产日期或有效期时必须指定批号");
    }

    @Test
    void explicitLotOutboundMutatesLotAndAggregateBalancesWithinTenantScope() {
        InventoryPostingCommand command = command("LOT-A", null, null);
        AuditMetadata audit = new AuditMetadata(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW);
        InventoryLotBalanceEntity lot = lotBalance();
        InventoryBalanceEntity balance = aggregateBalance();
        when(inventoryTransactionWriter.postedAllocations(
                command,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                "OUT"
        )).thenReturn(List.of());
        when(inventoryLotBalanceMapper.selectOne(any())).thenReturn(lot);
        when(inventoryLotBalanceMapper.updateById(lot)).thenReturn(1);
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(balance);
        when(inventoryBalanceMapper.updateById(balance)).thenReturn(1);

        List<LotAllocation> allocations = service.postOutbound(
                command,
                audit,
                "批次库存不足",
                product(1, 0),
                "LOT-A",
                new BigDecimal("2.0000")
        );

        assertThat(allocations).singleElement().satisfies(allocation -> {
            assertThat(allocation.lot()).isSameAs(lot);
            assertThat(allocation.qty()).isEqualByComparingTo("2.0000");
            assertThat(allocation.amount()).isEqualByComparingTo("20.00");
        });
        assertThat(lot.getQtyOnHand()).isEqualByComparingTo("3.0000");
        assertThat(lot.getAmountOnHand()).isEqualByComparingTo("30.00");
        assertThat(balance.getQtyOnHand()).isEqualByComparingTo("3.0000");
        assertThat(balance.getAmountOnHand()).isEqualByComparingTo("30.00");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryLotBalanceEntity>> lotWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryLotBalanceMapper).selectOne(lotWrapperCaptor.capture());
        assertTenantScoped(lotWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectOne(balanceWrapperCaptor.capture());
        assertTenantScoped(balanceWrapperCaptor.getValue());

        verify(inventoryTransactionWriter).insert(
                eq(command),
                eq("OUT"),
                eq(audit),
                eq(NOW),
                eq(new BigDecimal("2.0000")),
                eq(new BigDecimal("20.00")),
                eq("LOT-A"),
                eq(null),
                eq(null),
                eq("LOT-A")
        );
    }

    private InventoryPostingCommand command(String lotNo, LocalDate productionDate, LocalDate expiryDate) {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                PRODUCT_ID,
                "LOT_REFACTOR_TEST",
                "LOT-REFACTOR-1",
                9766L,
                new BigDecimal("2.0000"),
                BigDecimal.ZERO,
                "lot collaborator test",
                LocalDate.of(2026, 7, 23),
                lotNo,
                productionDate,
                expiryDate
        );
    }

    private ProductEntity product(int lotControlled, int shelfLifeControlled) {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setLotControlled(lotControlled);
        product.setShelfLifeControlled(shelfLifeControlled);
        return product;
    }

    private InventoryLotBalanceEntity lotBalance() {
        InventoryLotBalanceEntity lot = new InventoryLotBalanceEntity();
        lot.setCompanyId(COMPANY_ID);
        lot.setAccountBookId(ACCOUNT_BOOK_ID);
        lot.setWarehouseId(WAREHOUSE_ID);
        lot.setProductId(PRODUCT_ID);
        lot.setLotNo("LOT-A");
        lot.setQtyOnHand(new BigDecimal("5.0000"));
        lot.setQtyReserved(BigDecimal.ZERO);
        lot.setAmountOnHand(new BigDecimal("50.00"));
        return lot;
    }

    private InventoryBalanceEntity aggregateBalance() {
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setCompanyId(COMPANY_ID);
        balance.setAccountBookId(ACCOUNT_BOOK_ID);
        balance.setWarehouseId(WAREHOUSE_ID);
        balance.setProductId(PRODUCT_ID);
        balance.setQtyOnHand(new BigDecimal("5.0000"));
        balance.setQtyReserved(BigDecimal.ZERO);
        balance.setAmountOnHand(new BigDecimal("50.00"));
        return balance;
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
