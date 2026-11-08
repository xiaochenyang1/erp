package com.tuowei.erp.production.schedule;

import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.schedule.service.ProductionScheduleCalculator;
import com.tuowei.erp.production.schedule.web.ProductionScheduleItemResponse;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionScheduleCalculatorTest {
    private final ProductionScheduleCalculator calculator = new ProductionScheduleCalculator();

    @Test
    void allocatesSharedWorkCenterWithoutOverlapAndExplainsDueDateDelay() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        ProductionWorkCenterEntity center = center(101L, "WC-A", 480);
        ProductionOrderEntity first = order(1L, "MO-1", monday, monday);
        ProductionOrderEntity second = order(2L, "MO-2", monday, monday);
        ProductionOrderOperationEntity firstOperation = operation(11L, 1L, 101L, 1, "60", "4");
        ProductionOrderOperationEntity secondOperation = operation(21L, 2L, 101L, 1, "60", "5");

        var result = calculator.calculate(
                monday, monday.plusDays(1), List.of(first, second),
                List.of(firstOperation, secondOperation), List.of(center));

        ProductionScheduleItemResponse firstItem = result.items().get(0);
        ProductionScheduleItemResponse secondItem = result.items().get(1);
        assertEquals(LocalDateTime.of(monday, LocalTime.of(8, 0)), firstItem.scheduledStartTime());
        assertEquals(LocalDateTime.of(monday, LocalTime.of(12, 0)), firstItem.scheduledEndTime());
        assertEquals(firstItem.scheduledEndTime(), secondItem.scheduledStartTime());
        assertEquals(LocalDateTime.of(monday.plusDays(1), LocalTime.of(9, 0)), secondItem.scheduledEndTime());
        assertEquals(240L, secondItem.capacityWaitMinutes());
        assertFalse(firstItem.delayed());
        assertTrue(secondItem.delayed());
        assertEquals(new BigDecimal("540.00"), result.workCenters().get(0).scheduledMinutes());
        assertEquals(new BigDecimal("56.25"), result.workCenters().get(0).utilizationPercent());
    }

    @Test
    void preservesOperationPrecedenceAcrossDifferentWorkCenters() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        ProductionWorkCenterEntity cutting = center(101L, "CUT", 480);
        ProductionWorkCenterEntity assembly = center(102L, "ASM", 480);
        ProductionOrderEntity order = order(1L, "MO-1", monday, monday.plusDays(1));
        ProductionOrderOperationEntity first = operation(11L, 1L, 101L, 1, "30", "4");
        ProductionOrderOperationEntity second = operation(12L, 1L, 102L, 2, "15", "4");

        var result = calculator.calculate(
                monday, monday.plusDays(1), List.of(order), List.of(first, second), List.of(cutting, assembly));

        assertEquals(LocalDateTime.of(monday, LocalTime.of(10, 0)), result.items().get(0).scheduledEndTime());
        assertEquals(result.items().get(0).scheduledEndTime(), result.items().get(1).scheduledStartTime());
        assertEquals(LocalDateTime.of(monday, LocalTime.of(11, 0)), result.items().get(1).scheduledEndTime());
    }

    @Test
    void skipsWeekendsAndRejectsLoadsThatDoNotFitTheHorizon() {
        LocalDate friday = LocalDate.of(2026, 11, 13);
        ProductionWorkCenterEntity center = center(101L, "WC-A", 480);
        ProductionOrderEntity order = order(1L, "MO-1", friday, friday.plusDays(3));
        ProductionOrderOperationEntity operation = operation(11L, 1L, 101L, 1, "60", "10");

        var scheduled = calculator.calculate(
                friday, friday.plusDays(3), List.of(order), List.of(operation), List.of(center));
        assertEquals(LocalDateTime.of(friday, LocalTime.of(8, 0)), scheduled.items().get(0).scheduledStartTime());
        assertEquals(LocalDateTime.of(friday.plusDays(3), LocalTime.of(10, 0)), scheduled.items().get(0).scheduledEndTime());

        var insufficient = calculator.calculate(
                friday, friday, List.of(order), List.of(operation), List.of(center));
        assertEquals("UNSCHEDULED", insufficient.items().get(0).scheduleStatus());
        assertEquals("排程区间内工作中心产能不足", insufficient.items().get(0).reason());
    }

    @Test
    void rejectsInvalidAndExcessivelyLongHorizons() {
        LocalDate day = LocalDate.of(2026, 11, 9);
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(day, day.minusDays(1), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(day, day.plusDays(367), List.of(), List.of(), List.of()));
    }

    @Test
    void excludesDisabledWorkCentersFromCapacitySummaryButExplainsTheirOperations() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        ProductionWorkCenterEntity active = center(101L, "ACTIVE", 480);
        ProductionWorkCenterEntity disabled = center(102L, "DISABLED", 480);
        disabled.setStatus("DISABLED");
        ProductionOrderEntity order = order(1L, "MO-1", monday, monday);
        ProductionOrderOperationEntity operation = operation(11L, 1L, 102L, 1, "30", "1");

        var result = calculator.calculate(
                monday, monday, List.of(order), List.of(operation), List.of(active, disabled));

        assertEquals("UNSCHEDULED", result.items().get(0).scheduleStatus());
        assertEquals("工作中心不存在或已停用", result.items().get(0).reason());
        assertEquals(List.of(101L), result.workCenters().stream().map(item -> item.workCenterId()).toList());
    }

    private ProductionWorkCenterEntity center(Long id, String code, int capacityMinutes) {
        ProductionWorkCenterEntity center = new ProductionWorkCenterEntity();
        center.setId(id);
        center.setWorkCenterCode(code);
        center.setWorkCenterName(code);
        center.setDailyCapacityMinutes(capacityMinutes);
        center.setWorkdayStartTime(LocalTime.of(8, 0));
        center.setStatus("ACTIVE");
        return center;
    }

    private ProductionOrderEntity order(Long id, String orderNo, LocalDate start, LocalDate finish) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setProductId(id + 1000);
        order.setPlannedStartDate(start);
        order.setPlannedFinishDate(finish);
        return order;
    }

    private ProductionOrderOperationEntity operation(
            Long id,
            Long orderId,
            Long centerId,
            int lineNo,
            String minutes,
            String quantity
    ) {
        ProductionOrderOperationEntity operation = new ProductionOrderOperationEntity();
        operation.setId(id);
        operation.setOrderId(orderId);
        operation.setWorkCenterId(centerId);
        operation.setLineNo(lineNo);
        operation.setOperationCode("OP-" + lineNo);
        operation.setOperationName("Operation " + lineNo);
        operation.setStandardMinutes(new BigDecimal(minutes));
        operation.setPlannedQty(new BigDecimal(quantity));
        operation.setReportedQty(BigDecimal.ZERO);
        operation.setStatus("PENDING");
        return operation;
    }
}
