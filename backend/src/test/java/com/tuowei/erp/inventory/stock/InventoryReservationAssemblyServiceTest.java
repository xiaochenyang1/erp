package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import com.tuowei.erp.inventory.stock.service.InventoryReservationAssemblyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationAssemblyServiceTest {

    private final InventoryReservationAssemblyService service = new InventoryReservationAssemblyService();

    @Test
    void summarizesReservationsWithNormalizedKeysAndBalanceAvailability() {
        InventoryReservationEntity first = reservation(1L, 20L, 30L, " sales_order ", " active ", "5", "1", "4");
        InventoryReservationEntity second = reservation(2L, 20L, 30L, "SALES_ORDER", "ACTIVE", "3", null, "3");
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setWarehouseId(20L);
        balance.setProductId(30L);
        balance.setQtyOnHand(new BigDecimal("10"));
        balance.setQtyReserved(new BigDecimal("7"));

        var summaries = service.summarize(List.of(first, second), List.of(balance));

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.sourceType()).isEqualTo("SALES_ORDER");
            assertThat(summary.status()).isEqualTo("ACTIVE");
            assertThat(summary.reservedQty()).isEqualByComparingTo("8.0000");
            assertThat(summary.releasedQty()).isEqualByComparingTo("1.0000");
            assertThat(summary.remainingQty()).isEqualByComparingTo("7.0000");
            assertThat(summary.qtyOnHand()).isEqualByComparingTo("10.0000");
            assertThat(summary.qtyReserved()).isEqualByComparingTo("7.0000");
            assertThat(summary.qtyAvailable()).isEqualByComparingTo("3.0000");
            assertThat(summary.reservationCount()).isEqualTo(2);
        });
    }

    @Test
    void sourceGroupsEventsByReservationAndMapsQuantityPrecision() {
        InventoryReservationEntity first = reservation(1L, 20L, 30L, "SALES_ORDER", "ACTIVE", "5", "1", "4");
        InventoryReservationEntity second = reservation(2L, 20L, 30L, "SALES_ORDER", "ACTIVE", "2", "0", "2");
        InventoryReservationEventEntity firstEvent = event(11L, first.getId(), "RESERVE", "5", "0", "5");
        InventoryReservationEventEntity secondEvent = event(12L, second.getId(), "RELEASE", "1", "3", "2");

        var response = service.toSourceResponse(
                "SALES_ORDER",
                40L,
                "SO-40",
                List.of(first, second),
                List.of(firstEvent, secondEvent)
        );

        assertThat(response.sourceNo()).isEqualTo("SO-40");
        assertThat(response.reservations()).hasSize(2);
        assertThat(response.reservations().get(0).events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("RESERVE");
            assertThat(event.eventQty()).isEqualByComparingTo("5.0000");
        });
        assertThat(response.reservations().get(1).events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("RELEASE");
            assertThat(event.remainingQtyAfter()).isEqualByComparingTo("2.0000");
        });
    }

    @Test
    void detailDefaultsNullQuantitiesToScaledZero() {
        InventoryReservationEntity reservation = reservation(
                1L, 20L, 30L, "MANUAL", "ACTIVE", null, null, null);

        var detail = service.toDetailResponse(reservation, List.of());

        assertThat(detail.reservation().reservedQty()).isEqualByComparingTo("0.0000");
        assertThat(detail.reservation().releasedQty()).isEqualByComparingTo("0.0000");
        assertThat(detail.reservation().remainingQty()).isEqualByComparingTo("0.0000");
        assertThat(detail.events()).isEmpty();
    }

    private InventoryReservationEntity reservation(
            Long id,
            Long warehouseId,
            Long productId,
            String sourceType,
            String status,
            String reservedQty,
            String releasedQty,
            String remainingQty
    ) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(id);
        entity.setWarehouseId(warehouseId);
        entity.setProductId(productId);
        entity.setSourceType(sourceType);
        entity.setSourceId(40L);
        entity.setSourceNo("SO-40");
        entity.setSourceLineId(50L);
        entity.setReservedQty(decimal(reservedQty));
        entity.setReleasedQty(decimal(releasedQty));
        entity.setRemainingQty(decimal(remainingQty));
        entity.setStatus(status);
        entity.setCreatedTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        entity.setUpdatedTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        return entity;
    }

    private InventoryReservationEventEntity event(
            Long id,
            Long reservationId,
            String eventType,
            String eventQty,
            String before,
            String after
    ) {
        InventoryReservationEventEntity entity = new InventoryReservationEventEntity();
        entity.setId(id);
        entity.setReservationId(reservationId);
        entity.setEventType(eventType);
        entity.setEventQty(decimal(eventQty));
        entity.setRemainingQtyBefore(decimal(before));
        entity.setRemainingQtyAfter(decimal(after));
        entity.setCreatedTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        return entity;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
