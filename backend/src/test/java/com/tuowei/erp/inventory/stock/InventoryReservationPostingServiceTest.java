package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.inventory.stock.service.InventoryReservationPostingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationPostingServiceTest {

    @Mock
    private InventoryBalanceMapper inventoryBalanceMapper;

    @Mock
    private InventoryReservationMapper inventoryReservationMapper;

    @Mock
    private InventoryReservationEventMapper inventoryReservationEventMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryReservationEntity.class);
    }

    @Test
    void reserveIncreasesReservedQuantityAndWritesReservationEvent() {
        InventoryReservationPostingService service = new InventoryReservationPostingService(
                inventoryBalanceMapper,
                inventoryReservationMapper,
                inventoryReservationEventMapper
        );
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setCompanyId(1L);
        balance.setAccountBookId(2L);
        balance.setWarehouseId(10L);
        balance.setProductId(20L);
        balance.setQtyOnHand(new BigDecimal("10.0000"));
        balance.setQtyReserved(new BigDecimal("2.0000"));
        balance.setAmountOnHand(new BigDecimal("100.00"));
        balance.setVersion(0);
        when(inventoryBalanceMapper.selectOne(any())).thenReturn(balance);
        when(inventoryBalanceMapper.updateById(balance)).thenReturn(1);

        InventoryReservationCommand command = new InventoryReservationCommand(
                10L,
                20L,
                "SALES_ORDER",
                30L,
                "SO-001",
                40L,
                new BigDecimal("3.33333"),
                "reserve test"
        );
        AuditMetadata audit = new AuditMetadata(900L, 1L, 2L, LocalDateTime.of(2026, 6, 2, 9, 30));

        service.reserve(command, audit, "库存不足");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationLookupCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryReservationMapper).selectOne(reservationLookupCaptor.capture());
        assertTenantScoped(reservationLookupCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceLookupCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectOne(balanceLookupCaptor.capture());
        assertTenantScoped(balanceLookupCaptor.getValue());

        assertThat(balance.getQtyReserved()).isEqualByComparingTo("5.3333");
        assertThat(balance.getUpdatedBy()).isEqualTo(900L);
        assertThat(balance.getUpdatedTime()).isEqualTo(audit.now());

        ArgumentCaptor<InventoryReservationEntity> reservationCaptor = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        verify(inventoryReservationMapper).insert(reservationCaptor.capture());
        InventoryReservationEntity reservation = reservationCaptor.getValue();
        assertThat(reservation.getCompanyId()).isEqualTo(1L);
        assertThat(reservation.getAccountBookId()).isEqualTo(2L);
        assertThat(reservation.getWarehouseId()).isEqualTo(10L);
        assertThat(reservation.getProductId()).isEqualTo(20L);
        assertThat(reservation.getSourceType()).isEqualTo("SALES_ORDER");
        assertThat(reservation.getSourceId()).isEqualTo(30L);
        assertThat(reservation.getSourceNo()).isEqualTo("SO-001");
        assertThat(reservation.getSourceLineId()).isEqualTo(40L);
        assertThat(reservation.getReservedQty()).isEqualByComparingTo("3.3333");
        assertThat(reservation.getReleasedQty()).isEqualByComparingTo("0.0000");
        assertThat(reservation.getRemainingQty()).isEqualByComparingTo("3.3333");
        assertThat(reservation.getStatus()).isEqualTo("ACTIVE");
        assertThat(reservation.getRemark()).isEqualTo("reserve test");

        ArgumentCaptor<InventoryReservationEventEntity> eventCaptor = ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
        verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
        InventoryReservationEventEntity event = eventCaptor.getValue();
        assertThat(event.getCompanyId()).isEqualTo(1L);
        assertThat(event.getAccountBookId()).isEqualTo(2L);
        assertThat(event.getEventType()).isEqualTo("RESERVE");
        assertThat(event.getEventQty()).isEqualByComparingTo("3.3333");
        assertThat(event.getRemainingQtyBefore()).isEqualByComparingTo("0.0000");
        assertThat(event.getRemainingQtyAfter()).isEqualByComparingTo("3.3333");
        assertThat(event.getReason()).isEqualTo("reserve test");
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
