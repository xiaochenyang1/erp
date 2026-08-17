package com.tuowei.erp.sales.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesReturnPostingService {

    private static final int MAX_STATUS_REFRESH_ATTEMPTS = 8;

    private final SalesReturnMapper salesReturnMapper;
    private final SalesReturnLineMapper salesReturnLineMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesReturnQueryService salesReturnQueryService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;

    public SalesReturnPostingService(
            SalesReturnMapper salesReturnMapper,
            SalesReturnLineMapper salesReturnLineMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SalesReturnQueryService salesReturnQueryService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.salesReturnMapper = salesReturnMapper;
        this.salesReturnLineMapper = salesReturnLineMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.salesReturnQueryService = salesReturnQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SalesReturnResponse post(Long id) {
        SalesReturnEntity entity = requireReturn(id);
        salesReturnQueryService.assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售退货单状态不允许过账");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.SALES_RETURN, entity.getId());
        accountPeriodGuard.requireOpen(entity.getReturnDate(), "销售退货过账");

        SalesDeliveryEntity delivery = requirePostedDelivery(entity.getDeliveryId());
        salesReturnQueryService.assertCanView(delivery);
        SalesOrderEntity order = requireOrder(delivery.getOrderId());
        salesReturnQueryService.assertCanView(order);
        List<SalesReturnLineEntity> returnLines = salesReturnLineMapper.selectList(
                new LambdaQueryWrapper<SalesReturnLineEntity>()
                        .eq(SalesReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(SalesReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(SalesReturnLineEntity::getReturnId, entity.getId())
                        .orderByAsc(SalesReturnLineEntity::getLineNo)
        );
        Map<Long, SalesDeliveryLineEntity> deliveryLines = loadDeliveryLinesAsMap(delivery);
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        List<PostingLineContext> postingLines = preparePostingLines(
                returnLines,
                deliveryLines,
                orderLines,
                audit.companyId(),
                audit.accountBookId()
        );

        entity.setStatus("POSTED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesReturnMapper.updateById(entity),
                "销售退货单已被其他操作修改，请刷新后重试"
        );

        BigDecimal totalReturnCostAmount = BigDecimal.ZERO;
        for (PostingLineContext postingLine : postingLines) {
            SalesReturnLineEntity returnLine = postingLine.returnLine();
            SalesDeliveryLineEntity deliveryLine = postingLine.deliveryLine();
            SalesOrderLineEntity orderLine = postingLine.orderLine();
            BigDecimal qty = postingLine.qty();

            deliveryLine.setReturnedQty(ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(deliveryLine.getReturnedQty()).add(qty)
            ));
            deliveryLine.setUpdatedBy(audit.userId());
            deliveryLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    salesDeliveryLineMapper.updateById(deliveryLine),
                    "销售出库明细已被其他操作修改，请刷新后重试"
            );

            orderLine.setDeliveredQty(ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(orderLine.getDeliveredQty()).subtract(qty)
            ));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    salesOrderLineMapper.updateById(orderLine),
                    "销售订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postInbound(
                    new InventoryPostingCommand(
                            entity.getWarehouseId(),
                            returnLine.getProductId(),
                            "SALES_RETURN",
                            entity.getReturnNo(),
                            returnLine.getId(),
                            returnLine.getQty(),
                            postingLine.returnCostAmount(),
                            returnLine.getRemark(),
                            entity.getReturnDate(),
                            returnLine.getLotNo(),
                            returnLine.getProductionDate(),
                            returnLine.getExpiryDate(),
                            returnLine.getLocationId()
                    ),
                    audit
            );
            inventorySerialNumberService.registerInboundSerials(
                    returnLine.getProductId(),
                    entity.getWarehouseId(),
                    returnLine.getLocationId(),
                    returnLine.getSerialNos(),
                    "SALES_RETURN",
                    entity.getReturnNo(),
                    returnLine.getQty(),
                    audit
            );
            totalReturnCostAmount = ScalePrecision.amount(
                    totalReturnCostAmount.add(postingLine.returnCostAmount())
            );
        }

        refreshDeliveryStatus(delivery.getOrderId(), audit, now);
        financePostingService.recordSalesReturn(entity, order, totalReturnCostAmount, audit);
        return salesReturnQueryService.getById(id);
    }

    private List<PostingLineContext> preparePostingLines(
            List<SalesReturnLineEntity> returnLines,
            Map<Long, SalesDeliveryLineEntity> deliveryLines,
            Map<Long, SalesOrderLineEntity> orderLines,
            Long companyId,
            Long accountBookId
    ) {
        AccumulatedQuantityValidator returnQtyValidator =
                new AccumulatedQuantityValidator("退货数量超过销售出库明细剩余可退数量");
        AccumulatedQuantityValidator deliveredQtyValidator =
                new AccumulatedQuantityValidator("退货数量超过销售订单已出库数量");
        List<PostingLineContext> postingLines = new ArrayList<>(returnLines.size());
        for (SalesReturnLineEntity returnLine : returnLines) {
            SalesDeliveryLineEntity deliveryLine = requireDeliveryLine(deliveryLines, returnLine.getDeliveryLineId());
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, returnLine.getOrderLineId());
            BigDecimal qty = ScalePrecision.quantity(returnLine.getQty());
            returnQtyValidator.ensureWithinLimit(deliveryLine.getId(), qty, availableReturnQty(deliveryLine));
            deliveredQtyValidator.ensureWithinLimit(
                    orderLine.getId(),
                    qty,
                    ScalePrecision.zeroDefault(orderLine.getDeliveredQty())
            );
            validateReturnLotIntent(returnLine, deliveryLine, companyId, accountBookId);
            postingLines.add(new PostingLineContext(
                    returnLine,
                    deliveryLine,
                    orderLine,
                    qty,
                    resolveReturnCostAmount(returnLine, deliveryLine, companyId, accountBookId)
            ));
        }
        return postingLines;
    }

    private SalesReturnEntity requireReturn(Long id) {
        SalesReturnEntity entity = salesReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售退货单不存在");
        }
        return entity;
    }

    private SalesDeliveryEntity requirePostedDelivery(Long id) {
        SalesDeliveryEntity delivery = salesDeliveryMapper.selectById(id);
        if (delivery == null || delivery.getDeletedFlag() == null || delivery.getDeletedFlag() != 0
                || !"POSTED".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("销售出库单未过账，不能创建销售退货单");
        }
        return delivery;
    }

    private SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity order = salesOrderMapper.selectById(id);
        if (order == null || order.getDeletedFlag() == null || order.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        return order;
    }

    private Map<Long, SalesDeliveryLineEntity> loadDeliveryLinesAsMap(SalesDeliveryEntity delivery) {
        return salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId()))
                .stream()
                .collect(Collectors.toMap(SalesDeliveryLineEntity::getId, Function.identity()));
    }

    private Map<Long, SalesOrderLineEntity> loadOrderLinesAsMap(SalesOrderEntity order) {
        return salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                        .eq(SalesOrderLineEntity::getCompanyId, order.getCompanyId())
                        .eq(SalesOrderLineEntity::getAccountBookId, order.getAccountBookId())
                        .eq(SalesOrderLineEntity::getOrderId, order.getId()))
                .stream()
                .collect(Collectors.toMap(SalesOrderLineEntity::getId, Function.identity()));
    }

    private SalesDeliveryLineEntity requireDeliveryLine(
            Map<Long, SalesDeliveryLineEntity> deliveryLines,
            Long deliveryLineId
    ) {
        SalesDeliveryLineEntity deliveryLine = deliveryLines.get(deliveryLineId);
        if (deliveryLine == null) {
            throw new IllegalArgumentException("销售出库明细不存在");
        }
        return deliveryLine;
    }

    private SalesOrderLineEntity requireOrderLine(
            Map<Long, SalesOrderLineEntity> orderLines,
            Long orderLineId
    ) {
        SalesOrderLineEntity orderLine = orderLines.get(orderLineId);
        if (orderLine == null) {
            throw new IllegalArgumentException("销售订单明细不存在");
        }
        return orderLine;
    }

    private void refreshDeliveryStatus(Long orderId, AuditMetadata audit, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_STATUS_REFRESH_ATTEMPTS; attempt++) {
            SalesOrderEntity order = salesOrderMapper.selectById(orderId);
            if (order == null || order.getDeletedFlag() == null || order.getDeletedFlag() != 0) {
                throw new IllegalArgumentException("销售订单不存在");
            }
            List<SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order).values().stream().toList();
            order.setDeliveryStatus(resolveDeliveryStatus(orderLines));
            order.setUpdatedBy(audit.userId());
            order.setUpdatedTime(now);
            if (salesOrderMapper.updateById(order) == 1) {
                return;
            }
        }
        throw new BusinessConflictException("销售订单已被其他操作修改，请刷新后重试");
    }

    private String resolveDeliveryStatus(List<SalesOrderLineEntity> orderLines) {
        boolean anyDelivered = false;
        boolean allDelivered = !orderLines.isEmpty();
        for (SalesOrderLineEntity orderLine : orderLines) {
            BigDecimal orderedQty = ScalePrecision.quantity(orderLine.getQty());
            BigDecimal deliveredQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getDeliveredQty()));
            if (deliveredQty.compareTo(BigDecimal.ZERO) > 0) {
                anyDelivered = true;
            }
            if (deliveredQty.compareTo(orderedQty) < 0) {
                allDelivered = false;
            }
        }
        if (allDelivered) {
            return "FULL_DELIVERED";
        }
        if (anyDelivered) {
            return "PARTIAL_DELIVERED";
        }
        return "NOT_DELIVERED";
    }

    private void validateReturnLotIntent(
            SalesReturnLineEntity returnLine,
            SalesDeliveryLineEntity deliveryLine,
            Long companyId,
            Long accountBookId
    ) {
        if (StringUtils.hasText(returnLine.getLotNo())) {
            return;
        }
        List<InventoryTransactionEntity> deliveryLotTransactions = inventoryTransactionMapper.selectList(
                deliveryTransactionQuery(deliveryLine.getId(), companyId, accountBookId)
        );
        List<InventoryTransactionEntity> lotTransactions = deliveryLotTransactions.stream()
                .filter(txn -> StringUtils.hasText(txn.getLotNo()))
                .toList();
        Set<String> lots = lotTransactions.stream()
                .map(InventoryTransactionEntity::getLotNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (lots.size() > 1) {
            throw new IllegalArgumentException("销售退货必须指定批次号，原销售出库明细已拆分多个批次");
        }
        if (lots.size() == 1) {
            InventoryTransactionEntity transaction = lotTransactions.get(0);
            returnLine.setLotNo(transaction.getLotNo());
            returnLine.setProductionDate(transaction.getProductionDate());
            returnLine.setExpiryDate(transaction.getExpiryDate());
        }
    }

    private BigDecimal resolveReturnCostAmount(
            SalesReturnLineEntity returnLine,
            SalesDeliveryLineEntity deliveryLine,
            Long companyId,
            Long accountBookId
    ) {
        List<InventoryTransactionEntity> deliveryTransactions = inventoryTransactionMapper.selectList(
                deliveryTransactionQuery(deliveryLine.getId(), companyId, accountBookId)
        );
        if (deliveryTransactions.isEmpty()) {
            throw new IllegalStateException("销售出库库存分录不存在，不能按成本冲回");
        }
        String lotNo = normalizeNullableText(returnLine.getLotNo());
        List<InventoryTransactionEntity> matchedTransactions = deliveryTransactions;
        if (lotNo != null) {
            matchedTransactions = deliveryTransactions.stream()
                    .filter(transaction -> Objects.equals(
                            lotNo,
                            normalizeNullableText(transaction.getLotNo())
                    ))
                    .toList();
            if (matchedTransactions.isEmpty()) {
                throw new IllegalArgumentException("销售退货批次不存在原销售出库记录");
            }
        }
        BigDecimal totalQty = matchedTransactions.stream()
                .map(InventoryTransactionEntity::getQty)
                .map(ScalePrecision::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = matchedTransactions.stream()
                .map(InventoryTransactionEntity::getAmount)
                .map(ScalePrecision::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnQty = ScalePrecision.quantity(returnLine.getQty());
        if (totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("销售出库库存分录数量无效，不能按成本冲回");
        }
        if (returnQty.compareTo(totalQty) > 0) {
            throw new IllegalArgumentException("销售退货数量超过原销售出库库存数量");
        }
        if (returnQty.compareTo(totalQty) == 0) {
            return ScalePrecision.amount(totalAmount);
        }
        BigDecimal unitCost = ScalePrecision.unitCost(totalAmount, totalQty);
        return ScalePrecision.amount(unitCost.multiply(returnQty));
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> deliveryTransactionQuery(
            Long deliveryLineId,
            Long companyId,
            Long accountBookId
    ) {
        return new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, companyId)
                .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                .eq(InventoryTransactionEntity::getBizType, "SALES_DELIVERY")
                .eq(InventoryTransactionEntity::getBizLineId, deliveryLineId)
                .eq(InventoryTransactionEntity::getDirection, "OUT")
                .orderByAsc(InventoryTransactionEntity::getId);
    }

    private BigDecimal availableReturnQty(SalesDeliveryLineEntity deliveryLine) {
        return ScalePrecision.quantity(
                ScalePrecision.quantity(deliveryLine.getQty())
                        .subtract(ScalePrecision.quantity(ScalePrecision.zeroDefault(deliveryLine.getReturnedQty())))
        );
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record PostingLineContext(
            SalesReturnLineEntity returnLine,
            SalesDeliveryLineEntity deliveryLine,
            SalesOrderLineEntity orderLine,
            BigDecimal qty,
            BigDecimal returnCostAmount
    ) {
    }
}
