package com.tuowei.erp.masterdata;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseStockSummaryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseStockSummaryServiceTest {

    @BeforeAll
    static void initTableInfo() {
        init(WarehouseEntity.class);
        init(InventoryBalanceEntity.class);
    }

    @Test
    void aggregatesPositiveStockBalancesInWarehouse() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        InventoryBalanceMapper balanceMapper = mock(InventoryBalanceMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        when(auditFactory.current()).thenReturn(new AuditMetadata(9L, 1L, 1L, LocalDateTime.parse("2026-07-22T10:00:00")));
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(701L);
        when(warehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance("10", "3", "100"), balance("5", "1", "60")));

        var response = new WarehouseStockSummaryService(warehouseMapper, balanceMapper, auditFactory).summary(701L);

        assertThat(response.skuCount()).isEqualTo(2);
        assertThat(response.qtyOnHand()).isEqualByComparingTo("15.0000");
        assertThat(response.qtyReserved()).isEqualByComparingTo("4.0000");
        assertThat(response.qtyAvailable()).isEqualByComparingTo("11.0000");
        assertThat(response.amountOnHand()).isEqualByComparingTo("160.00");
    }

    private static InventoryBalanceEntity balance(String onHand, String reserved, String amount) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setQtyOnHand(new BigDecimal(onHand));
        entity.setQtyReserved(new BigDecimal(reserved));
        entity.setAmountOnHand(new BigDecimal(amount));
        return entity;
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
    }
}
