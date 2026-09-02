package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import com.tuowei.erp.inventory.stock.web.InventoryReservationDetailResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationEventResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReservationAssemblyService {

    public InventoryReservationResponse toReservationResponse(InventoryReservationEntity reservation) {
        return new InventoryReservationResponse(
                reservation.getId(),
                reservation.getWarehouseId(),
                reservation.getProductId(),
                reservation.getSourceType(),
                reservation.getSourceId(),
                reservation.getSourceNo(),
                reservation.getSourceLineId(),
                quantity(reservation.getReservedQty()),
                quantity(reservation.getReleasedQty()),
                quantity(reservation.getRemainingQty()),
                reservation.getStatus(),
                reservation.getRemark(),
                reservation.getCreatedTime(),
                reservation.getUpdatedTime()
        );
    }

    public InventoryReservationDetailResponse toDetailResponse(
            InventoryReservationEntity reservation,
            List<InventoryReservationEventEntity> events
    ) {
        return new InventoryReservationDetailResponse(
                toReservationResponse(reservation),
                events.stream().map(this::toEventResponse).toList()
        );
    }

    public InventoryReservationSourceResponse toSourceResponse(
            String sourceType,
            Long sourceId,
            String sourceNo,
            List<InventoryReservationEntity> reservations,
            List<InventoryReservationEventEntity> events
    ) {
        Map<Long, List<InventoryReservationEventEntity>> eventsByReservationId = events.stream()
                .collect(Collectors.groupingBy(
                        InventoryReservationEventEntity::getReservationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return new InventoryReservationSourceResponse(
                sourceType,
                sourceId,
                sourceNo,
                reservations.stream()
                        .map(reservation -> toDetailResponse(
                                reservation,
                                eventsByReservationId.getOrDefault(reservation.getId(), List.of())
                        ))
                        .toList()
        );
    }

    public List<InventoryReservationSummaryResponse> summarize(
            List<InventoryReservationEntity> reservations,
            List<InventoryBalanceEntity> balanceRows
    ) {
        Map<BalanceKey, InventoryBalanceEntity> balances = balanceRows.stream()
                .collect(Collectors.toMap(
                        balance -> new BalanceKey(balance.getWarehouseId(), balance.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<SummaryKey, SummaryAccumulator> summaries = new LinkedHashMap<>();
        for (InventoryReservationEntity reservation : reservations) {
            SummaryKey key = new SummaryKey(
                    reservation.getWarehouseId(),
                    reservation.getProductId(),
                    normalizeUpper(reservation.getSourceType()),
                    normalizeUpper(reservation.getStatus())
            );
            summaries.computeIfAbsent(key, SummaryAccumulator::new).add(reservation);
        }
        return summaries.values().stream()
                .map(accumulator -> accumulator.toResponse(
                        balances.get(new BalanceKey(accumulator.key.warehouseId(), accumulator.key.productId()))
                ))
                .sorted(Comparator.comparing(InventoryReservationSummaryResponse::warehouseId)
                        .thenComparing(InventoryReservationSummaryResponse::productId)
                        .thenComparing(
                                InventoryReservationSummaryResponse::sourceType,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(
                                InventoryReservationSummaryResponse::status,
                                Comparator.nullsLast(String::compareTo)
                        ))
                .toList();
    }

    private InventoryReservationEventResponse toEventResponse(InventoryReservationEventEntity event) {
        return new InventoryReservationEventResponse(
                event.getId(),
                event.getReservationId(),
                event.getEventType(),
                quantity(event.getEventQty()),
                quantity(event.getRemainingQtyBefore()),
                quantity(event.getRemainingQtyAfter()),
                event.getReason(),
                event.getCreatedBy(),
                event.getCreatedTime()
        );
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private BigDecimal quantity(BigDecimal value) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(value));
    }

    private record BalanceKey(Long warehouseId, Long productId) {
    }

    private record SummaryKey(Long warehouseId, Long productId, String sourceType, String status) {
    }

    private class SummaryAccumulator {

        private final SummaryKey key;
        private BigDecimal reservedQty = quantity(BigDecimal.ZERO);
        private BigDecimal releasedQty = quantity(BigDecimal.ZERO);
        private BigDecimal remainingQty = quantity(BigDecimal.ZERO);
        private long count;

        private SummaryAccumulator(SummaryKey key) {
            this.key = key;
        }

        private void add(InventoryReservationEntity reservation) {
            reservedQty = quantity(reservedQty.add(quantity(reservation.getReservedQty())));
            releasedQty = quantity(releasedQty.add(quantity(reservation.getReleasedQty())));
            remainingQty = quantity(remainingQty.add(quantity(reservation.getRemainingQty())));
            count++;
        }

        private InventoryReservationSummaryResponse toResponse(InventoryBalanceEntity balance) {
            BigDecimal qtyOnHand = balance == null ? quantity(BigDecimal.ZERO) : quantity(balance.getQtyOnHand());
            BigDecimal qtyReserved = balance == null ? quantity(BigDecimal.ZERO) : quantity(balance.getQtyReserved());
            BigDecimal qtyAvailable = quantity(qtyOnHand.subtract(qtyReserved));
            return new InventoryReservationSummaryResponse(
                    key.warehouseId(),
                    key.productId(),
                    key.sourceType(),
                    key.status(),
                    reservedQty,
                    releasedQty,
                    remainingQty,
                    qtyOnHand,
                    qtyReserved,
                    qtyAvailable,
                    count
            );
        }
    }
}
