package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryTransactionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionWriterTest {

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @Test
    void insertCreatesScaledTransactionWithLotMetadata() {
        InventoryTransactionWriter writer = new InventoryTransactionWriter(inventoryTransactionMapper);
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 10, 30);
        InventoryPostingCommand command = new InventoryPostingCommand(
                7001L,
                8001L,
                "TEST_BIZ",
                "BIZ-001",
                9001L,
                new BigDecimal("3.33333"),
                new BigDecimal("99.999"),
                "writer test",
                LocalDate.of(2026, 5, 28),
                "LOT-A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31)
        );
        AuditMetadata audit = new AuditMetadata(3L, 1L, 2L, now);

        writer.insert(
                command,
                "OUT",
                audit,
                now,
                new BigDecimal("3.33333"),
                new BigDecimal("99.999"),
                "LOT-A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31),
                "LOT-A"
        );

        ArgumentCaptor<InventoryTransactionEntity> captor = ArgumentCaptor.forClass(InventoryTransactionEntity.class);
        verify(inventoryTransactionMapper).insert(captor.capture());
        InventoryTransactionEntity transaction = captor.getValue();
        assertThat(transaction.getCompanyId()).isEqualTo(1L);
        assertThat(transaction.getAccountBookId()).isEqualTo(2L);
        assertThat(transaction.getWarehouseId()).isEqualTo(7001L);
        assertThat(transaction.getProductId()).isEqualTo(8001L);
        assertThat(transaction.getBizType()).isEqualTo("TEST_BIZ");
        assertThat(transaction.getBizNo()).isEqualTo("BIZ-001");
        assertThat(transaction.getBizLineId()).isEqualTo(9001L);
        assertThat(transaction.getDirection()).isEqualTo("OUT");
        assertThat(transaction.getQty()).isEqualByComparingTo("3.3333");
        assertThat(transaction.getAmount()).isEqualByComparingTo("100.00");
        assertThat(transaction.getUnitCost()).isEqualByComparingTo("30.0003");
        assertThat(transaction.getOccurredTime()).isEqualTo(LocalDate.of(2026, 5, 28).atStartOfDay());
        assertThat(transaction.getLotNo()).isEqualTo("LOT-A");
        assertThat(transaction.getProductionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(transaction.getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(transaction.getLotKey()).isEqualTo("LOT-A");
        assertThat(transaction.getRemark()).isEqualTo("writer test");
        assertThat(transaction.getCreatedBy()).isEqualTo(3L);
        assertThat(transaction.getCreatedTime()).isEqualTo(now);
        assertThat(transaction.getUpdatedBy()).isEqualTo(3L);
        assertThat(transaction.getUpdatedTime()).isEqualTo(now);
        assertThat(transaction.getVersion()).isZero();
    }
}
