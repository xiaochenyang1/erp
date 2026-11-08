package com.tuowei.erp.production.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.schedule.web.ProductionScheduleItemResponse;
import com.tuowei.erp.production.schedule.web.ProductionScheduleRequest;
import com.tuowei.erp.production.schedule.web.ProductionScheduleResponse;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads tenant scheduling inputs, delegates deterministic capacity allocation and commits the resulting operation plan. */
@Service
public class ProductionScheduleService {
    private static final String AUDIT_TYPE = "BUSINESS";
    private static final String BUSINESS_TYPE = "PRODUCTION_SCHEDULE";
    private static final String ACTION_RUN = "RUN";

    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderOperationMapper operationMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final ProductionScheduleCalculator calculator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public ProductionScheduleService(
            ProductionOrderMapper orderMapper,
            ProductionOrderOperationMapper operationMapper,
            ProductionWorkCenterMapper workCenterMapper,
            ProductionScheduleCalculator calculator,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            SystemLogService systemLogService,
            ObjectMapper objectMapper
    ) {
        this.orderMapper = orderMapper;
        this.operationMapper = operationMapper;
        this.workCenterMapper = workCenterMapper;
        this.calculator = calculator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.systemLogService = systemLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProductionScheduleResponse preview(ProductionScheduleRequest request) {
        return calculate(request);
    }

    @Transactional
    public ProductionScheduleResponse run(ProductionScheduleRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        Loaded loaded = load(request, audit);
        ProductionScheduleCalculator.Calculation calculation = calculator.calculate(
                request.startDate(), request.endDate(), loaded.orders(), loaded.operations(), loaded.workCenters());
        Map<Long, ProductionScheduleItemResponse> itemByOperation = calculation.items().stream()
                .collect(Collectors.toMap(ProductionScheduleItemResponse::operationId, Function.identity()));
        LocalDateTime scheduledTime = audit.now();
        for (ProductionOrderOperationEntity operation : loaded.operations()) {
            if ("DONE".equals(operation.getStatus())) continue;
            ProductionScheduleItemResponse item = itemByOperation.get(operation.getId());
            if (item != null && ProductionScheduleCalculator.STATUS_SCHEDULED.equals(item.scheduleStatus())) {
                operation.setScheduledMinutes(item.requiredMinutes());
                operation.setScheduledStartTime(item.scheduledStartTime());
                operation.setScheduledEndTime(item.scheduledEndTime());
                operation.setScheduledBy(audit.userId());
                operation.setScheduledTime(scheduledTime);
            } else {
                operation.setScheduledMinutes(null);
                operation.setScheduledStartTime(null);
                operation.setScheduledEndTime(null);
                operation.setScheduledBy(null);
                operation.setScheduledTime(null);
            }
            operation.setUpdatedBy(audit.userId());
            operation.setUpdatedTime(scheduledTime);
            OptimisticLockGuard.requireUpdated(
                    operationMapper.updateById(operation),
                    "生产排程已被其他操作修改，请刷新后重试"
            );
        }
        ProductionScheduleResponse response = response(request, true, scheduledTime, calculation);
        recordAudit(response, audit.userId(), currentUserContext.requireCurrentUser().username());
        return response;
    }

    private ProductionScheduleResponse calculate(ProductionScheduleRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        Loaded loaded = load(request, audit);
        ProductionScheduleCalculator.Calculation calculation = calculator.calculate(
                request.startDate(), request.endDate(), loaded.orders(), loaded.operations(), loaded.workCenters());
        return response(request, false, audit.now(), calculation);
    }

    private Loaded load(ProductionScheduleRequest request, AuditMetadata audit) {
        if (request == null || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("排程开始和结束日期不能为空");
        }
        List<ProductionOrderEntity> orders = orderMapper.selectList(new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getCompanyId, audit.companyId())
                .eq(ProductionOrderEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionOrderEntity::getDeletedFlag, 0)
                .in(ProductionOrderEntity::getStatus,
                        ProductionOrderService.STATUS_RELEASED,
                        ProductionOrderService.STATUS_MATERIAL_ISSUED)
                .le(ProductionOrderEntity::getPlannedStartDate, request.endDate())
                .orderByAsc(ProductionOrderEntity::getPlannedFinishDate)
                .orderByAsc(ProductionOrderEntity::getOrderNo));
        if (orders.isEmpty()) return new Loaded(List.of(), List.of(), List.of());
        List<Long> orderIds = orders.stream().map(ProductionOrderEntity::getId).toList();
        List<ProductionOrderOperationEntity> operations = operationMapper.selectList(
                new LambdaQueryWrapper<ProductionOrderOperationEntity>()
                        .eq(ProductionOrderOperationEntity::getCompanyId, audit.companyId())
                        .eq(ProductionOrderOperationEntity::getAccountBookId, audit.accountBookId())
                        .eq(ProductionOrderOperationEntity::getDeletedFlag, 0)
                        .in(ProductionOrderOperationEntity::getOrderId, orderIds)
                        .orderByAsc(ProductionOrderOperationEntity::getOrderId)
                        .orderByAsc(ProductionOrderOperationEntity::getLineNo));
        Set<Long> workCenterIds = operations.stream().map(ProductionOrderOperationEntity::getWorkCenterId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        List<ProductionWorkCenterEntity> workCenters = workCenterIds.isEmpty() ? List.of()
                : workCenterMapper.selectList(new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                        .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                        .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                        .eq(ProductionWorkCenterEntity::getDeletedFlag, 0)
                        .in(ProductionWorkCenterEntity::getId, workCenterIds));
        return new Loaded(orders, operations, workCenters);
    }

    private ProductionScheduleResponse response(
            ProductionScheduleRequest request,
            boolean committed,
            LocalDateTime generatedTime,
            ProductionScheduleCalculator.Calculation calculation
    ) {
        int scheduled = 0;
        int delayed = 0;
        int unscheduled = 0;
        Set<Long> plannedOrders = new LinkedHashSet<>();
        for (ProductionScheduleItemResponse item : calculation.items()) {
            plannedOrders.add(item.orderId());
            if (ProductionScheduleCalculator.STATUS_SCHEDULED.equals(item.scheduleStatus())) {
                scheduled++;
                if (item.delayed()) delayed++;
            } else {
                unscheduled++;
            }
        }
        return new ProductionScheduleResponse(
                request.startDate(), request.endDate(), committed,
                plannedOrders.size(), calculation.items().size(), scheduled,
                unscheduled, delayed, generatedTime,
                List.copyOf(calculation.items()), List.copyOf(calculation.workCenters())
        );
    }

    private void recordAudit(ProductionScheduleResponse response, Long operatorId, String operatorName) {
        String message = String.format(
                "有限产能排程：工单 %d 张，已排 %d 道，未排 %d 道，延期 %d 道",
                response.orderCount(), response.scheduledCount(), response.unscheduledCount(), response.delayedCount());
        systemLogService.recordAudit(
                AUDIT_TYPE, BUSINESS_TYPE, null,
                response.startDate() + "~" + response.endDate(), ACTION_RUN,
                operatorId, operatorName, toJson(response), message, response.generatedTime()
        );
    }

    private String toJson(ProductionScheduleResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生产排程证据序列化失败", exception);
        }
    }

    private record Loaded(
            List<ProductionOrderEntity> orders,
            List<ProductionOrderOperationEntity> operations,
            List<ProductionWorkCenterEntity> workCenters
    ) {
    }
}
