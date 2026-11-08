package com.tuowei.erp.production.schedule;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.schedule.service.ProductionScheduleCalculator;
import com.tuowei.erp.production.schedule.service.ProductionScheduleService;
import com.tuowei.erp.production.schedule.web.ProductionScheduleRequest;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionScheduleServiceTest {
    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 9);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(7L, 1L, 1L, NOW);

    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderOperationMapper operationMapper = mock(ProductionOrderOperationMapper.class);
    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final SystemLogService systemLogService = mock(SystemLogService.class);
    private final ProductionScheduleService service = new ProductionScheduleService(
            orderMapper,
            operationMapper,
            workCenterMapper,
            new ProductionScheduleCalculator(),
            auditMetadataFactory,
            currentUserContext,
            systemLogService,
            new ObjectMapper().findAndRegisterModules()
    );

    private ProductionOrderEntity order;
    private ProductionOrderOperationEntity scheduledOperation;
    private ProductionOrderOperationEntity unscheduledOperation;
    private ProductionWorkCenterEntity center;

    @BeforeEach
    void setup() {
        order = order();
        scheduledOperation = operation(11L, 101L, 1, "60", "2");
        unscheduledOperation = operation(12L, 999L, 2, "30", "1");
        unscheduledOperation.setScheduledMinutes(new BigDecimal("30.00"));
        unscheduledOperation.setScheduledStartTime(START_DATE.atTime(13, 0));
        unscheduledOperation.setScheduledEndTime(START_DATE.atTime(13, 30));
        unscheduledOperation.setScheduledBy(99L);
        unscheduledOperation.setScheduledTime(NOW.minusDays(1));
        center = center();

        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(currentUserContext.requireCurrentUser()).thenReturn(new CurrentUser(
                7L, 1L, 1L, 10L, 20L, "planner", "Planner"
        ));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));
        when(operationMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(scheduledOperation, unscheduledOperation));
        when(workCenterMapper.selectList(any(Wrapper.class))).thenReturn(List.of(center));
        when(operationMapper.updateById(any(ProductionOrderOperationEntity.class))).thenReturn(1);
    }

    @Test
    void previewsWithoutMutatingOperationSnapshots() {
        var response = service.preview(new ProductionScheduleRequest(START_DATE, END_DATE));

        assertThat(response.committed()).isFalse();
        assertThat(response.orderCount()).isEqualTo(1);
        assertThat(response.operationCount()).isEqualTo(2);
        assertThat(response.scheduledCount()).isEqualTo(1);
        assertThat(response.unscheduledCount()).isEqualTo(1);
        assertThat(response.items()).extracting("scheduleStatus")
                .containsExactly("SCHEDULED", "UNSCHEDULED");
        assertThat(response.workCenters()).singleElement().satisfies(summary -> {
            assertThat(summary.workCenterId()).isEqualTo(101L);
            assertThat(summary.scheduledMinutes()).isEqualByComparingTo("120.00");
        });
        verify(operationMapper, never()).updateById(any(ProductionOrderOperationEntity.class));
        verify(systemLogService, never()).recordAudit(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commitsScheduledFieldsClearsStalePlanAndRecordsAuditEvidence() {
        var response = service.run(new ProductionScheduleRequest(START_DATE, END_DATE));

        assertThat(response.committed()).isTrue();
        assertThat(scheduledOperation.getScheduledMinutes()).isEqualByComparingTo("120.00");
        assertThat(scheduledOperation.getScheduledStartTime()).isEqualTo(START_DATE.atTime(8, 0));
        assertThat(scheduledOperation.getScheduledEndTime()).isEqualTo(START_DATE.atTime(10, 0));
        assertThat(scheduledOperation.getScheduledBy()).isEqualTo(7L);
        assertThat(scheduledOperation.getScheduledTime()).isEqualTo(NOW);
        assertThat(scheduledOperation.getUpdatedBy()).isEqualTo(7L);
        assertThat(scheduledOperation.getUpdatedTime()).isEqualTo(NOW);

        assertThat(unscheduledOperation.getScheduledMinutes()).isNull();
        assertThat(unscheduledOperation.getScheduledStartTime()).isNull();
        assertThat(unscheduledOperation.getScheduledEndTime()).isNull();
        assertThat(unscheduledOperation.getScheduledBy()).isNull();
        assertThat(unscheduledOperation.getScheduledTime()).isNull();
        verify(operationMapper).updateById(scheduledOperation);
        verify(operationMapper).updateById(unscheduledOperation);

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(systemLogService).recordAudit(
                eq("BUSINESS"),
                eq("PRODUCTION_SCHEDULE"),
                eq(null),
                eq("2026-11-09~2026-11-10"),
                eq("RUN"),
                eq(7L),
                eq("planner"),
                snapshot.capture(),
                eq("有限产能排程：工单 1 张，已排 1 道，未排 1 道，延期 0 道"),
                eq(NOW)
        );
        assertThat(snapshot.getValue()).contains("\"committed\":true").contains("\"operationId\":11");
    }

    private ProductionOrderEntity order() {
        ProductionOrderEntity value = new ProductionOrderEntity();
        value.setId(1L);
        value.setCompanyId(1L);
        value.setAccountBookId(1L);
        value.setOrderNo("MO-001");
        value.setProductId(1001L);
        value.setPlannedStartDate(START_DATE);
        value.setPlannedFinishDate(END_DATE);
        value.setStatus("RELEASED");
        value.setDeletedFlag(0);
        return value;
    }

    private ProductionOrderOperationEntity operation(
            Long id,
            Long workCenterId,
            int lineNo,
            String standardMinutes,
            String plannedQty
    ) {
        ProductionOrderOperationEntity value = new ProductionOrderOperationEntity();
        value.setId(id);
        value.setCompanyId(1L);
        value.setAccountBookId(1L);
        value.setOrderId(1L);
        value.setLineNo(lineNo);
        value.setOperationCode("OP-" + lineNo);
        value.setOperationName("Operation " + lineNo);
        value.setWorkCenterId(workCenterId);
        value.setStandardMinutes(new BigDecimal(standardMinutes));
        value.setPlannedQty(new BigDecimal(plannedQty));
        value.setReportedQty(BigDecimal.ZERO);
        value.setStatus("PENDING");
        value.setDeletedFlag(0);
        value.setVersion(0);
        return value;
    }

    private ProductionWorkCenterEntity center() {
        ProductionWorkCenterEntity value = new ProductionWorkCenterEntity();
        value.setId(101L);
        value.setCompanyId(1L);
        value.setAccountBookId(1L);
        value.setWorkCenterCode("WC-001");
        value.setWorkCenterName("Assembly");
        value.setDailyCapacityMinutes(480);
        value.setWorkdayStartTime(LocalTime.of(8, 0));
        value.setStatus("ACTIVE");
        value.setDeletedFlag(0);
        return value;
    }
}
