package com.tuowei.erp.production.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.model.ProductionOrderOperationEntity;
import com.tuowei.erp.production.operation.web.ProductionOperationReportRequest;
import com.tuowei.erp.production.operation.web.ProductionOrderOperationResponse;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Generates operation snapshots and records production operation reports. */
@Service
public class ProductionOperationCommandService {
    private final ProductionOrderOperationMapper operationMapper;
    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionOperationQueryService queryService;

    public ProductionOperationCommandService(
            ProductionOrderOperationMapper operationMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            AuditMetadataFactory auditMetadataFactory,
            ProductionOperationQueryService queryService
    ) {
        this.operationMapper = operationMapper;
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    /** Generates a routing snapshot after release; no active routing means no completion gate. */
    @Transactional
    public void generateForReleasedOrder(ProductionOrderEntity order, AuditMetadata audit) {
        long existing = operationMapper.selectCount(new LambdaQueryWrapper<ProductionOrderOperationEntity>()
                .eq(ProductionOrderOperationEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderOperationEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderOperationEntity::getOrderId, order.getId())
                .eq(ProductionOrderOperationEntity::getDeletedFlag, 0));
        if (existing > 0 || order.getBomId() == null) return;
        ProductionRoutingEntity routing = routingMapper.selectOne(new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionRoutingEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionRoutingEntity::getBomId, order.getBomId())
                .eq(ProductionRoutingEntity::getStatus, "ACTIVE")
                .eq(ProductionRoutingEntity::getDeletedFlag, 0)
                .last("LIMIT 1"));
        if (routing == null) return;
        List<ProductionRoutingOperationEntity> routingOperations = routingOperationMapper.selectList(
                new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                        .eq(ProductionRoutingOperationEntity::getCompanyId, order.getCompanyId())
                        .eq(ProductionRoutingOperationEntity::getAccountBookId, order.getAccountBookId())
                        .eq(ProductionRoutingOperationEntity::getRoutingId, routing.getId())
                        .orderByAsc(ProductionRoutingOperationEntity::getLineNo));
        if (routingOperations.isEmpty()) return;
        LocalDateTime now = audit.now();
        BigDecimal planned = ScalePrecision.quantity(ScalePrecision.zeroDefault(order.getPlannedQty()));
        for (ProductionRoutingOperationEntity routingOperation : routingOperations) {
            ProductionOrderOperationEntity operation = new ProductionOrderOperationEntity();
            operation.setCompanyId(order.getCompanyId()); operation.setAccountBookId(order.getAccountBookId());
            operation.setOrderId(order.getId()); operation.setRoutingId(routing.getId());
            operation.setRoutingOperationId(routingOperation.getId()); operation.setLineNo(routingOperation.getLineNo());
            operation.setOperationCode(routingOperation.getOperationCode()); operation.setOperationName(routingOperation.getOperationName());
            operation.setWorkCenterId(routingOperation.getWorkCenterId()); operation.setPlannedQty(planned);
            operation.setReportedQty(BigDecimal.ZERO.setScale(4)); operation.setQualifiedQty(BigDecimal.ZERO.setScale(4));
            operation.setScrapQty(BigDecimal.ZERO.setScale(4)); operation.setStatus(ProductionOperationService.STATUS_PENDING);
            operation.setDeletedFlag(0); operation.setRemark(routingOperation.getRemark());
            operation.setCreatedBy(audit.userId()); operation.setCreatedTime(now);
            operation.setUpdatedBy(audit.userId()); operation.setUpdatedTime(now); operation.setVersion(0);
            operationMapper.insert(operation);
        }
    }

    @Transactional
    public ProductionOrderOperationResponse report(
            Long orderId,
            Long operationId,
            ProductionOperationReportRequest request
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = queryService.requireOrder(orderId);
        if (ProductionOrderService.STATUS_DRAFT.equals(order.getStatus())
                || ProductionOrderService.STATUS_CANCELLED.equals(order.getStatus())
                || ProductionOrderService.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前工单状态不允许工序报工");
        }
        ProductionOrderOperationEntity operation = queryService.requireOperation(order, operationId);
        BigDecimal reportQty = ScalePrecision.quantity(request.reportQty());
        BigDecimal qualifiedQty = ScalePrecision.quantity(request.qualifiedQty());
        BigDecimal scrapQty = ScalePrecision.quantity(
                request.scrapQty() == null ? reportQty.subtract(qualifiedQty) : request.scrapQty());
        if (qualifiedQty.compareTo(reportQty) > 0) throw new IllegalArgumentException("合格数量不能大于报工数量");
        if (scrapQty.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("报废数量不能为负");
        if (qualifiedQty.add(scrapQty).compareTo(reportQty) > 0) throw new IllegalArgumentException("合格+报废不能大于报工数量");

        BigDecimal newReported = ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getReportedQty()).add(reportQty));
        BigDecimal newQualified = ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getQualifiedQty()).add(qualifiedQty));
        BigDecimal newScrap = ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getScrapQty()).add(scrapQty));
        BigDecimal planned = ScalePrecision.quantity(ScalePrecision.zeroDefault(operation.getPlannedQty()));
        if (newReported.compareTo(planned) > 0) {
            throw new IllegalArgumentException("累计报工数量不能超过计划数量 " + planned.toPlainString());
        }
        operation.setReportedQty(newReported); operation.setQualifiedQty(newQualified); operation.setScrapQty(newScrap);
        if (newQualified.compareTo(planned) >= 0) operation.setStatus(ProductionOperationService.STATUS_DONE);
        else if (newReported.compareTo(BigDecimal.ZERO) > 0) operation.setStatus(ProductionOperationService.STATUS_IN_PROGRESS);
        if (StringUtils.hasText(request.remark())) operation.setRemark(request.remark().trim());
        operation.setUpdatedBy(audit.userId()); operation.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(operationMapper.updateById(operation), "工序记录已被其他操作修改，请刷新后重试");
        Map<Long, String> names = queryService.loadWorkCenterNames(List.of(operation), order);
        return queryService.toResponse(operation, names);
    }
}
