package com.tuowei.erp.production.schedule.service;

import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.schedule.web.ProductionScheduleItemResponse;
import com.tuowei.erp.production.schedule.web.ProductionScheduleWorkCenterResponse;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic earliest-due-date scheduler with operation precedence and finite weekday work-center capacity. */
@Component
public class ProductionScheduleCalculator {
    static final String STATUS_SCHEDULED = "SCHEDULED";
    static final String STATUS_UNSCHEDULED = "UNSCHEDULED";
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(8, 0);
    private static final int DEFAULT_CAPACITY_MINUTES = 480;

    public Calculation calculate(
            LocalDate startDate,
            LocalDate endDate,
            List<ProductionOrderEntity> orders,
            List<ProductionOrderOperationEntity> operations,
            List<ProductionWorkCenterEntity> workCenters
    ) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("排程结束日期不能早于开始日期");
        }
        if (Duration.between(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()).toDays() > 366) {
            throw new IllegalArgumentException("单次排程区间不能超过366天");
        }

        Map<Long, ProductionWorkCenterEntity> centerById = new LinkedHashMap<>();
        workCenters.stream()
                .sorted(Comparator.comparing(ProductionWorkCenterEntity::getWorkCenterCode,
                        Comparator.nullsLast(String::compareTo)))
                .forEach(center -> centerById.put(center.getId(), center));
        Map<Long, List<ProductionOrderOperationEntity>> operationsByOrder = new HashMap<>();
        for (ProductionOrderOperationEntity operation : operations) {
            operationsByOrder.computeIfAbsent(operation.getOrderId(), ignored -> new ArrayList<>()).add(operation);
        }
        operationsByOrder.values().forEach(list -> list.sort(Comparator
                .comparing(ProductionOrderOperationEntity::getLineNo, Comparator.nullsLast(Integer::compareTo))));

        List<ProductionOrderEntity> sortedOrders = orders.stream().sorted(Comparator
                .comparing(ProductionOrderEntity::getPlannedFinishDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(ProductionOrderEntity::getPlannedStartDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(ProductionOrderEntity::getOrderNo, Comparator.nullsLast(String::compareTo)))
                .toList();
        Map<Long, LocalDateTime> centerCursor = new HashMap<>();
        Map<Long, BigDecimal> centerLoad = new HashMap<>();
        List<ProductionScheduleItemResponse> items = new ArrayList<>();

        for (ProductionOrderEntity order : sortedOrders) {
            LocalDate orderStart = order.getPlannedStartDate() == null || order.getPlannedStartDate().isBefore(startDate)
                    ? startDate : order.getPlannedStartDate();
            LocalDateTime predecessorEnd = orderStart.atStartOfDay();
            String predecessorFailure = null;
            for (ProductionOrderOperationEntity operation : operationsByOrder.getOrDefault(order.getId(), List.of())) {
                if ("DONE".equals(operation.getStatus())) continue;
                ProductionWorkCenterEntity center = centerById.get(operation.getWorkCenterId());
                BigDecimal remainingQty = positiveRemaining(operation);
                BigDecimal standardMinutes = scaleMinutes(operation.getStandardMinutes());
                BigDecimal requiredMinutes = scaleMinutes(standardMinutes.multiply(remainingQty));

                if (predecessorFailure != null) {
                    items.add(unscheduled(order, operation, center, remainingQty, standardMinutes, requiredMinutes,
                            "前置工序未排入计划：" + predecessorFailure));
                    continue;
                }
                if (center == null || !"ACTIVE".equals(center.getStatus())) {
                    predecessorFailure = "工作中心不存在或已停用";
                    items.add(unscheduled(order, operation, center, remainingQty, standardMinutes, requiredMinutes,
                            predecessorFailure));
                    continue;
                }
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (standardMinutes.compareTo(BigDecimal.ZERO) <= 0 || requiredMinutes.compareTo(BigDecimal.ZERO) <= 0) {
                    predecessorFailure = "工序标准工时必须大于0";
                    items.add(unscheduled(order, operation, center, remainingQty, standardMinutes, requiredMinutes,
                            predecessorFailure));
                    continue;
                }

                LocalDateTime readyTime = predecessorEnd;
                LocalDateTime earliest = alignToWorkWindow(readyTime, center);
                LocalDateTime cursor = centerCursor.get(center.getId());
                LocalDateTime available = cursor == null || cursor.isBefore(earliest) ? earliest : cursor;
                Allocation allocation = allocate(available, requiredMinutes, center, endDate);
                if (allocation == null) {
                    predecessorFailure = "排程区间内工作中心产能不足";
                    items.add(unscheduled(order, operation, center, remainingQty, standardMinutes, requiredMinutes,
                            predecessorFailure));
                    continue;
                }

                centerCursor.put(center.getId(), allocation.end());
                centerLoad.merge(center.getId(), requiredMinutes, BigDecimal::add);
                predecessorEnd = allocation.end();
                LocalDateTime due = order.getPlannedFinishDate() == null
                        ? endDate.atTime(LocalTime.MAX)
                        : order.getPlannedFinishDate().atTime(LocalTime.MAX);
                boolean delayed = allocation.end().isAfter(due);
                long lateMinutes = delayed ? Math.max(1, Duration.between(due, allocation.end()).toMinutes()) : 0;
                long waitMinutes = allocation.start().isAfter(earliest)
                        ? Duration.between(earliest, allocation.start()).toMinutes() : 0;
                items.add(new ProductionScheduleItemResponse(
                        operation.getId(), order.getId(), order.getOrderNo(), order.getProductId(),
                        order.getPlannedFinishDate(), operation.getLineNo(), operation.getOperationCode(),
                        operation.getOperationName(), center.getId(), center.getWorkCenterCode(),
                        center.getWorkCenterName(), remainingQty, standardMinutes, requiredMinutes,
                        allocation.start(), allocation.end(), waitMinutes, delayed, lateMinutes,
                        STATUS_SCHEDULED,
                        delayed ? "受工序前置关系或工作中心产能约束，预计晚于计划完工日期" : null
                ));
            }
        }

        List<ProductionScheduleWorkCenterResponse> summaries = centerById.values().stream()
                .filter(center -> "ACTIVE".equals(center.getStatus()))
                .map(center -> summary(center, startDate, endDate, centerLoad.getOrDefault(center.getId(), BigDecimal.ZERO)))
                .toList();
        return new Calculation(items, summaries);
    }

    private Allocation allocate(
            LocalDateTime available,
            BigDecimal requiredMinutes,
            ProductionWorkCenterEntity center,
            LocalDate horizonEnd
    ) {
        long remaining = requiredMinutes.setScale(0, RoundingMode.CEILING).longValueExact();
        LocalDateTime current = alignToWorkWindow(available, center);
        if (current.toLocalDate().isAfter(horizonEnd)) return null;
        LocalDateTime first = current;
        while (remaining > 0) {
            if (current.toLocalDate().isAfter(horizonEnd)) return null;
            LocalDateTime dayEnd = current.toLocalDate().atTime(startTime(center)).plusMinutes(capacity(center));
            long availableMinutes = Math.max(0, Duration.between(current, dayEnd).toMinutes());
            if (availableMinutes == 0) {
                current = alignToWorkWindow(current.toLocalDate().plusDays(1).atStartOfDay(), center);
                continue;
            }
            long allocated = Math.min(remaining, availableMinutes);
            current = current.plusMinutes(allocated);
            remaining -= allocated;
            if (remaining > 0) {
                current = alignToWorkWindow(current.toLocalDate().plusDays(1).atStartOfDay(), center);
            }
        }
        return new Allocation(first, current);
    }

    private LocalDateTime alignToWorkWindow(LocalDateTime candidate, ProductionWorkCenterEntity center) {
        LocalDate date = candidate.toLocalDate();
        while (isWeekend(date)) date = date.plusDays(1);
        LocalDateTime start = date.atTime(startTime(center));
        LocalDateTime end = start.plusMinutes(capacity(center));
        if (candidate.toLocalDate().isBefore(date) || candidate.isBefore(start)) return start;
        if (!candidate.isBefore(end)) {
            LocalDate next = date.plusDays(1);
            while (isWeekend(next)) next = next.plusDays(1);
            return next.atTime(startTime(center));
        }
        return candidate.withSecond(0).withNano(0);
    }

    private ProductionScheduleItemResponse unscheduled(
            ProductionOrderEntity order,
            ProductionOrderOperationEntity operation,
            ProductionWorkCenterEntity center,
            BigDecimal remainingQty,
            BigDecimal standardMinutes,
            BigDecimal requiredMinutes,
            String reason
    ) {
        return new ProductionScheduleItemResponse(
                operation.getId(), order.getId(), order.getOrderNo(), order.getProductId(),
                order.getPlannedFinishDate(), operation.getLineNo(), operation.getOperationCode(),
                operation.getOperationName(), operation.getWorkCenterId(),
                center == null ? null : center.getWorkCenterCode(),
                center == null ? null : center.getWorkCenterName(),
                remainingQty, standardMinutes, requiredMinutes, null, null, 0L,
                false, 0L, STATUS_UNSCHEDULED, reason
        );
    }

    private ProductionScheduleWorkCenterResponse summary(
            ProductionWorkCenterEntity center,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal load
    ) {
        long workdays = startDate.datesUntil(endDate.plusDays(1)).filter(date -> !isWeekend(date)).count();
        BigDecimal capacity = BigDecimal.valueOf(workdays).multiply(BigDecimal.valueOf(capacity(center))).setScale(2);
        BigDecimal scheduled = scaleMinutes(load);
        BigDecimal utilization = capacity.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : scheduled.multiply(BigDecimal.valueOf(100)).divide(capacity, 2, RoundingMode.HALF_UP);
        return new ProductionScheduleWorkCenterResponse(
                center.getId(), center.getWorkCenterCode(), center.getWorkCenterName(), capacity(center),
                startTime(center), capacity, scheduled, utilization
        );
    }

    private BigDecimal positiveRemaining(ProductionOrderOperationEntity operation) {
        BigDecimal planned = Objects.requireNonNullElse(operation.getPlannedQty(), BigDecimal.ZERO);
        BigDecimal reported = Objects.requireNonNullElse(operation.getReportedQty(), BigDecimal.ZERO);
        return planned.subtract(reported).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMinutes(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private int capacity(ProductionWorkCenterEntity center) {
        Integer capacity = center.getDailyCapacityMinutes();
        return capacity == null || capacity < 1 ? DEFAULT_CAPACITY_MINUTES : capacity;
    }

    private LocalTime startTime(ProductionWorkCenterEntity center) {
        return center.getWorkdayStartTime() == null ? DEFAULT_START_TIME : center.getWorkdayStartTime();
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public record Calculation(
            List<ProductionScheduleItemResponse> items,
            List<ProductionScheduleWorkCenterResponse> workCenters
    ) {
    }

    private record Allocation(LocalDateTime start, LocalDateTime end) {
    }
}
