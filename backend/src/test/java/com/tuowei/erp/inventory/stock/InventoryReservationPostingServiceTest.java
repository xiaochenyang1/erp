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
import java.util.List;
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
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(balance));
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
        verify(inventoryBalanceMapper).selectList(balanceLookupCaptor.capture());
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


    @Test
    void reserveAggregatesAvailableQtyAcrossLocations() {
        InventoryReservationPostingService service = new InventoryReservationPostingService(
                inventoryBalanceMapper,
                inventoryReservationMapper,
                inventoryReservationEventMapper
        );
        InventoryBalanceEntity first = new InventoryBalanceEntity();
        first.setId(1L);
        first.setCompanyId(1L);
        first.setAccountBookId(2L);
        first.setWarehouseId(10L);
        first.setProductId(20L);
        first.setLocationId(100L);
        first.setQtyOnHand(new BigDecimal("2.0000"));
        first.setQtyReserved(new BigDecimal("0.0000"));
        first.setVersion(0);

        InventoryBalanceEntity second = new InventoryBalanceEntity();
        second.setId(2L);
        second.setCompanyId(1L);
        second.setAccountBookId(2L);
        second.setWarehouseId(10L);
        second.setProductId(20L);
        second.setLocationId(200L);
        second.setQtyOnHand(new BigDecimal("5.0000"));
        second.setQtyReserved(new BigDecimal("1.0000"));
        second.setVersion(0);

        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(inventoryBalanceMapper.updateById(any(InventoryBalanceEntity.class))).thenReturn(1);

        InventoryReservationCommand command = new InventoryReservationCommand(
                10L,
                20L,
                "SALES_ORDER",
                30L,
                "SO-LOC",
                41L,
                new BigDecimal("4.0000"),
                "multi-location reserve"
        );
        AuditMetadata audit = new AuditMetadata(900L, 1L, 2L, LocalDateTime.of(2026, 6, 2, 9, 30));
        service.reserve(command, audit, "库存不足");

        assertThat(first.getQtyReserved()).isEqualByComparingTo("2.0000");
        assertThat(second.getQtyReserved()).isEqualByComparingTo("3.0000");
    }

    @Test
    void releaseReservationUpdatesReservationAndReleasesLatestLocationFirst() {
        InventoryReservationPostingService service = new InventoryReservationPostingService(
                inventoryBalanceMapper,
                inventoryReservationMapper,
                inventoryReservationEventMapper
        );
        InventoryReservationEntity reservation = reservation("4.0000", "1.0000");
        InventoryBalanceEntity first = balance(1L, "5.0000", "1.0000");
        InventoryBalanceEntity second = balance(2L, "5.0000", "3.0000");
        when(inventoryReservationMapper.selectOne(any())).thenReturn(reservation);
        when(inventoryReservationMapper.updateById(reservation)).thenReturn(1);
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(inventoryBalanceMapper.updateById(any(InventoryBalanceEntity.class))).thenReturn(1);
        AuditMetadata audit = new AuditMetadata(900L, 1L, 2L, LocalDateTime.of(2026, 6, 2, 10, 0));

        service.releaseReservation("SALES_ORDER", 40L, new BigDecimal("2.0000"), audit);

        assertThat(reservation.getReleasedQty()).isEqualByComparingTo("3.0000");
        assertThat(reservation.getRemainingQty()).isEqualByComparingTo("2.0000");
        assertThat(reservation.getStatus()).isEqualTo("ACTIVE");
        assertThat(first.getQtyReserved()).isEqualByComparingTo("1.0000");
        assertThat(second.getQtyReserved()).isEqualByComparingTo("1.0000");

        ArgumentCaptor<InventoryReservationEventEntity> eventCaptor =
                ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
        verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RELEASE");
        assertThat(eventCaptor.getValue().getRemainingQtyBefore()).isEqualByComparingTo("4.0000");
        assertThat(eventCaptor.getValue().getRemainingQtyAfter()).isEqualByComparingTo("2.0000");
    }

    @Test
    void restoreReservationReallocatesFromFirstAvailableLocationAndWritesReason() {
        InventoryReservationPostingService service = new InventoryReservationPostingService(
                inventoryBalanceMapper,
                inventoryReservationMapper,
                inventoryReservationEventMapper
        );
        InventoryReservationEntity reservation = reservation("1.0000", "3.0000");
        InventoryBalanceEntity first = balance(1L, "5.0000", "2.0000");
        InventoryBalanceEntity second = balance(2L, "5.0000", "0.0000");
        when(inventoryReservationMapper.selectOne(any())).thenReturn(reservation);
        when(inventoryReservationMapper.updateById(reservation)).thenReturn(1);
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(inventoryBalanceMapper.updateById(any(InventoryBalanceEntity.class))).thenReturn(1);
        AuditMetadata audit = new AuditMetadata(900L, 1L, 2L, LocalDateTime.of(2026, 6, 2, 10, 30));

        service.restoreReservation(
                "PRODUCTION_ORDER", 40L, new BigDecimal("2.0000"), audit, "生产退料恢复"
        );

        assertThat(reservation.getReleasedQty()).isEqualByComparingTo("1.0000");
        assertThat(reservation.getRemainingQty()).isEqualByComparingTo("3.0000");
        assertThat(reservation.getStatus()).isEqualTo("ACTIVE");
        assertThat(first.getQtyReserved()).isEqualByComparingTo("4.0000");
        assertThat(second.getQtyReserved()).isEqualByComparingTo("0.0000");

        ArgumentCaptor<InventoryReservationEventEntity> eventCaptor =
                ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
        verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RESTORE");
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("生产退料恢复");
    }

    private InventoryReservationEntity reservation(String remainingQty, String releasedQty) {
        InventoryReservationEntity reservation = new InventoryReservationEntity();
        reservation.setId(50L);
        reservation.setCompanyId(1L);
        reservation.setAccountBookId(2L);
        reservation.setWarehouseId(10L);
        reservation.setProductId(20L);
        reservation.setSourceType("SALES_ORDER");
        reservation.setSourceId(30L);
        reservation.setSourceNo("SO-001");
        reservation.setSourceLineId(40L);
        reservation.setReservedQty(new BigDecimal("5.0000"));
        reservation.setReleasedQty(new BigDecimal(releasedQty));
        reservation.setRemainingQty(new BigDecimal(remainingQty));
        reservation.setStatus("ACTIVE");
        reservation.setVersion(0);
        return reservation;
    }

    private InventoryBalanceEntity balance(Long id, String qtyOnHand, String qtyReserved) {
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setId(id);
        balance.setCompanyId(1L);
        balance.setAccountBookId(2L);
        balance.setWarehouseId(10L);
        balance.setProductId(20L);
        balance.setQtyOnHand(new BigDecimal(qtyOnHand));
        balance.setQtyReserved(new BigDecimal(qtyReserved));
        balance.setVersion(0);
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
