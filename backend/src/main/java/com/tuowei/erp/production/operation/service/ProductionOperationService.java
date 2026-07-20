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
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductionOperationService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";

    private final ProductionOrderOperationMapper operationMapper;
    private final ProductionOrderMapper orderMapper;
    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionOperationService(
            ProductionOrderOperationMapper operationMapper,
            ProductionOrderMapper orderMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.operationMapper = operationMapper;
        this.orderMapper = orderMapper;
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    /**
     * 工单释放后按 BOM 绑定工艺路线生成工序快照；无工艺路线则跳过（完工不闸门）。
     */
    @Transactional
    public void generateForReleasedOrder(ProductionOrderEntity order, AuditMetadata audit) {
        long existing = operationMapper.selectCount(new LambdaQueryWrapper<ProductionOrderOperationEntity>()
                .eq(ProductionOrderOperationEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderOperationEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderOperationEntity::getOrderId, order.getId())
                .eq(ProductionOrderOperationEntity::getDeletedFlag, 0));
        if (existing > 0) {
            return;
        }
        if (order.getBomId() == null) {
            return;
        }
        ProductionRoutingEntity routing = routingMapper.selectOne(new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionRoutingEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionRoutingEntity::getBomId, order.getBomId())
                .eq(ProductionRoutingEntity::getStatus, "ACTIVE")
                .eq(ProductionRoutingEntity::getDeletedFlag, 0)
                .last("LIMIT 1"));
        if (routing == null) {
            return;
        }
        List<ProductionRoutingOperationEntity> routingOps = routingOperationMapper.selectList(
                new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                        .eq(ProductionRoutingOperationEntity::getCompanyId, order.getCompanyId())
                        .eq(ProductionRoutingOperationEntity::getAccountBookId, order.getAccountBookId())
                        .eq(ProductionRoutingOperationEntity::getRoutingId, routing.getId())
                        .orderByAsc(ProductionRoutingOperationEntity::getLineNo)
        );
        if (routingOps.isEmpty()) {
            return;
        }
        LocalDateTime now = audit.now();
        BigDecimal planned = ScalePrecision.quantity(ScalePrecision.zeroDefault(order.getPlannedQty()));
        for (ProductionRoutingOperationEntity routingOp : routingOps) {
            ProductionOrderOperationEntity op = new ProductionOrderOperationEntity();
            op.setCompanyId(order.getCompanyId());
            op.setAccountBookId(order.getAccountBookId());
            op.setOrderId(order.getId());
            op.setRoutingId(routing.getId());
            op.setRoutingOperationId(routingOp.getId());
            op.setLineNo(routingOp.getLineNo());
            op.setOperationCode(routingOp.getOperationCode());
            op.setOperationName(routingOp.getOperationName());
            op.setWorkCenterId(routingOp.getWorkCenterId());
            op.setPlannedQty(planned);
            op.setReportedQty(BigDecimal.ZERO.setScale(4));
            op.setQualifiedQty(BigDecimal.ZERO.setScale(4));
            op.setScrapQty(BigDecimal.ZERO.setScale(4));
            op.setStatus(STATUS_PENDING);
            op.setDeletedFlag(0);
            op.setRemark(routingOp.getRemark());
            op.setCreatedBy(audit.userId());
            op.setCreatedTime(now);
            op.setUpdatedBy(audit.userId());
            op.setUpdatedTime(now);
            op.setVersion(0);
            operationMapper.insert(op);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderOperationResponse> listByOrder(Long orderId) {
        ProductionOrderEntity order = requireOrder(orderId);
        List<ProductionOrderOperationEntity> ops = loadOps(order);
        Map<Long, String> workCenterNames = loadWorkCenterNames(ops, order);
        return ops.stream().map(op -> toResponse(op, workCenterNames)).toList();
    }

    @Transactional
    public ProductionOrderOperationResponse report(Long orderId, Long operationId, ProductionOperationReportRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireOrder(orderId);
        if (ProductionOrderService.STATUS_DRAFT.equals(order.getStatus())
                || ProductionOrderService.STATUS_CANCELLED.equals(order.getStatus())
                || ProductionOrderService.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前工单状态不允许工序报工");
        }
        ProductionOrderOperationEntity op = requireOperation(order, operationId);
        BigDecimal reportQty = ScalePrecision.quantity(request.reportQty());
        BigDecimal qualifiedQty = ScalePrecision.quantity(request.qualifiedQty());
        BigDecimal scrapQty = ScalePrecision.quantity(
                request.scrapQty() == null ? reportQty.subtract(qualifiedQty) : request.scrapQty()
        );
        if (qualifiedQty.compareTo(reportQty) > 0) {
            throw new IllegalArgumentException("合格数量不能大于报工数量");
        }
        if (scrapQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("报废数量不能为负");
        }
        if (qualifiedQty.add(scrapQty).compareTo(reportQty) > 0) {
            throw new IllegalArgumentException("合格+报废不能大于报工数量");
        }

        BigDecimal newReported = ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getReportedQty()).add(reportQty));
        BigDecimal newQualified = ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getQualifiedQty()).add(qualifiedQty));
        BigDecimal newScrap = ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getScrapQty()).add(scrapQty));
        BigDecimal planned = ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getPlannedQty()));
        if (newReported.compareTo(planned) > 0) {
            throw new IllegalArgumentException("累计报工数量不能超过计划数量 " + planned.toPlainString());
        }

        op.setReportedQty(newReported);
        op.setQualifiedQty(newQualified);
        op.setScrapQty(newScrap);
        if (newQualified.compareTo(planned) >= 0) {
            op.setStatus(STATUS_DONE);
        } else if (newReported.compareTo(BigDecimal.ZERO) > 0) {
            op.setStatus(STATUS_IN_PROGRESS);
        }
        if (StringUtils.hasText(request.remark())) {
            op.setRemark(request.remark().trim());
        }
        op.setUpdatedBy(audit.userId());
        op.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                operationMapper.updateById(op),
                "工序记录已被其他操作修改，请刷新后重试"
        );
        Map<Long, String> names = loadWorkCenterNames(List.of(op), order);
        return toResponse(op, names);
    }

    /**
     * 若工单存在工序快照，则全部工序须 DONE，且各工序合格量 >= 本次完工量。
     */
    @Transactional(readOnly = true)
    public void assertReadyForCompletion(ProductionOrderEntity order, BigDecimal completionQty) {
        List<ProductionOrderOperationEntity> ops = loadOps(order);
        if (ops.isEmpty()) {
            return;
        }
        for (ProductionOrderOperationEntity op : ops) {
            if (!STATUS_DONE.equals(op.getStatus())) {
                throw new IllegalArgumentException(
                        "工序未完成报工，不能完工：第 " + op.getLineNo() + " 道 " + op.getOperationName()
                );
            }
            BigDecimal qualified = ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getQualifiedQty()));
            if (qualified.compareTo(ScalePrecision.quantity(completionQty)) < 0) {
                throw new IllegalArgumentException(String.format(
                        "工序合格量不足，不能完工：%s 合格 %s < 完工 %s",
                        op.getOperationName(),
                        qualified.toPlainString(),
                        ScalePrecision.quantity(completionQty).toPlainString()
                ));
            }
        }
    }

    private List<ProductionOrderOperationEntity> loadOps(ProductionOrderEntity order) {
        return operationMapper.selectList(new LambdaQueryWrapper<ProductionOrderOperationEntity>()
                .eq(ProductionOrderOperationEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderOperationEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderOperationEntity::getOrderId, order.getId())
                .eq(ProductionOrderOperationEntity::getDeletedFlag, 0)
                .orderByAsc(ProductionOrderOperationEntity::getLineNo));
    }

    private ProductionOrderEntity requireOrder(Long orderId) {
        ProductionOrderEntity order = orderMapper.selectById(orderId);
        if (order == null || Integer.valueOf(1).equals(order.getDeletedFlag())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        if (!Objects.equals(order.getCompanyId(), audit.companyId())
                || !Objects.equals(order.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        return order;
    }

    private ProductionOrderOperationEntity requireOperation(ProductionOrderEntity order, Long operationId) {
        ProductionOrderOperationEntity op = operationMapper.selectById(operationId);
        if (op == null
                || !Objects.equals(op.getOrderId(), order.getId())
                || !Objects.equals(op.getCompanyId(), order.getCompanyId())
                || Integer.valueOf(1).equals(op.getDeletedFlag())) {
            throw new IllegalArgumentException("工序不存在");
        }
        return op;
    }

    private Map<Long, String> loadWorkCenterNames(List<ProductionOrderOperationEntity> ops, ProductionOrderEntity order) {
        Set<Long> ids = ops.stream()
                .map(ProductionOrderOperationEntity::getWorkCenterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workCenterMapper.selectBatchIds(ids).stream()
                .filter(wc -> Objects.equals(wc.getCompanyId(), order.getCompanyId()))
                .collect(Collectors.toMap(
                        ProductionWorkCenterEntity::getId,
                        ProductionWorkCenterEntity::getWorkCenterName,
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    private ProductionOrderOperationResponse toResponse(
            ProductionOrderOperationEntity op,
            Map<Long, String> workCenterNames
    ) {
        return new ProductionOrderOperationResponse(
                op.getId(),
                op.getOrderId(),
                op.getLineNo(),
                op.getOperationCode(),
                op.getOperationName(),
                op.getWorkCenterId(),
                workCenterNames.get(op.getWorkCenterId()),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getPlannedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getReportedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getQualifiedQty())),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(op.getScrapQty())),
                op.getStatus(),
                op.getRemark()
        );
    }
}
