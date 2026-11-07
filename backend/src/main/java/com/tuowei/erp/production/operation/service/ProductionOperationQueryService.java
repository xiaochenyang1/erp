package com.tuowei.erp.production.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.operation.web.ProductionOrderOperationResponse;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-side tenant guards, operation loading and display hydration. */
@Service
public class ProductionOperationQueryService {
    private final ProductionOrderOperationMapper operationMapper;
    private final ProductionOrderMapper orderMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionOperationQueryService(
            ProductionOrderOperationMapper operationMapper,
            ProductionOrderMapper orderMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.operationMapper = operationMapper;
        this.orderMapper = orderMapper;
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderOperationResponse> listByOrder(Long orderId) {
        ProductionOrderEntity order = requireOrder(orderId);
        List<ProductionOrderOperationEntity> operations = loadOperations(order);
        Map<Long, String> workCenterNames = loadWorkCenterNames(operations, order);
        return operations.stream().map(operation -> toResponse(operation, workCenterNames)).toList();
    }

    /** Existing operation snapshots gate completion; orders without snapshots remain compatible. */
    @Transactional(readOnly = true)
    public void assertReadyForCompletion(ProductionOrderEntity order, BigDecimal completionQty) {
        List<ProductionOrderOperationEntity> operations = loadOperations(order);
        if (operations.isEmpty()) return;
        for (ProductionOrderOperationEntity operation : operations) {
            if (!ProductionOperationService.STATUS_DONE.equals(operation.getStatus())) {
                throw new IllegalArgumentException("工序未完成报工，不能完工：第 "
                        + operation.getLineNo() + " 道 " + operation.getOperationName());
            }
            BigDecimal qualified = ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getQualifiedQty()));
            if (qualified.compareTo(ScalePrecision.quantity(completionQty)) < 0) {
                throw new IllegalArgumentException(String.format(
                        "工序合格量不足，不能完工：%s 合格 %s < 完工 %s",
                        operation.getOperationName(), qualified.toPlainString(),
                        ScalePrecision.quantity(completionQty).toPlainString()
                ));
            }
        }
    }

    ProductionOrderEntity requireOrder(Long orderId) {
        ProductionOrderEntity order = orderMapper.selectById(orderId);
        AuditMetadata audit = auditMetadataFactory.current();
        if (order == null || Integer.valueOf(1).equals(order.getDeletedFlag())
                || !Objects.equals(order.getCompanyId(), audit.companyId())
                || !Objects.equals(order.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        return order;
    }

    ProductionOrderOperationEntity requireOperation(ProductionOrderEntity order, Long operationId) {
        ProductionOrderOperationEntity operation = operationMapper.selectById(operationId);
        if (operation == null
                || !Objects.equals(operation.getOrderId(), order.getId())
                || !Objects.equals(operation.getCompanyId(), order.getCompanyId())
                || !Objects.equals(operation.getAccountBookId(), order.getAccountBookId())
                || Integer.valueOf(1).equals(operation.getDeletedFlag())) {
            throw new IllegalArgumentException("工序不存在");
        }
        return operation;
    }

    List<ProductionOrderOperationEntity> loadOperations(ProductionOrderEntity order) {
        return operationMapper.selectList(new LambdaQueryWrapper<ProductionOrderOperationEntity>()
                .eq(ProductionOrderOperationEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderOperationEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderOperationEntity::getOrderId, order.getId())
                .eq(ProductionOrderOperationEntity::getDeletedFlag, 0)
                .orderByAsc(ProductionOrderOperationEntity::getLineNo));
    }

    Map<Long, String> loadWorkCenterNames(
            List<ProductionOrderOperationEntity> operations,
            ProductionOrderEntity order
    ) {
        Set<Long> ids = operations.stream().map(ProductionOrderOperationEntity::getWorkCenterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return workCenterMapper.selectList(new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                        .eq(ProductionWorkCenterEntity::getCompanyId, order.getCompanyId())
                        .eq(ProductionWorkCenterEntity::getAccountBookId, order.getAccountBookId())
                        .eq(ProductionWorkCenterEntity::getDeletedFlag, 0)
                        .in(ProductionWorkCenterEntity::getId, ids)).stream()
                .filter(workCenter -> Objects.equals(workCenter.getCompanyId(), order.getCompanyId()))
                .filter(workCenter -> Objects.equals(workCenter.getAccountBookId(), order.getAccountBookId()))
                .collect(Collectors.toMap(ProductionWorkCenterEntity::getId,
                        ProductionWorkCenterEntity::getWorkCenterName, (left, right) -> left, HashMap::new));
    }

    ProductionOrderOperationResponse toResponse(
            ProductionOrderOperationEntity operation,
            Map<Long, String> workCenterNames
    ) {
        return new ProductionOrderOperationResponse(
                operation.getId(), operation.getOrderId(), operation.getLineNo(), operation.getOperationCode(),
                operation.getOperationName(), operation.getWorkCenterId(), workCenterNames.get(operation.getWorkCenterId()),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getPlannedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getReportedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getQualifiedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getScrapQty())),
                operation.getStatus(), operation.getRemark()
        );
    }
}
