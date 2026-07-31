package com.tuowei.erp.sales.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesDeliveryPostingService {

    private static final String OUTBOUND_SHORTAGE_MESSAGE = "库存不足，不能执行销售出库";
    private static final String POST_RESERVATION_SHORTAGE_MESSAGE = "销售订单预占数量不足，不能执行销售出库";
    private static final int MAX_STATUS_REFRESH_ATTEMPTS = 8;

    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryReservationMapper inventoryReservationMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final SalesDeliveryQueryService salesDeliveryQueryService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final ProductValidator productValidator;
    private final QcInspectionGate qcInspectionGate;

    public SalesDeliveryPostingService(
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            WarehouseMapper warehouseMapper,
            InventoryReservationMapper inventoryReservationMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            SalesDeliveryQueryService salesDeliveryQueryService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            ProductValidator productValidator,
            QcInspectionGate qcInspectionGate
    ) {
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.inventoryReservationMapper = inventoryReservationMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.salesDeliveryQueryService = salesDeliveryQueryService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.productValidator = productValidator;
        this.qcInspectionGate = qcInspectionGate;
    }

    @Transactional
    public SalesDeliveryResponse post(Long id) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        salesDeliveryQueryService.assertCanView(delivery);
        if (!"DRAFT".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("当前销售出库单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(delivery.getDeliveryDate(), "销售出库过账");

        SalesOrderEntity order = requireApprovedOrder(delivery.getOrderId(), "销售订单未审批通过，不能执行出库过账");
        salesDeliveryQueryService.assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(delivery.getWarehouseId(), audit.companyId(), audit.accountBookId());
        assertWarehouseMatchesOrder(delivery.getWarehouseId(), order.getWarehouseId());
        List<SalesDeliveryLineEntity> deliveryLines = salesDeliveryLineMapper.selectList(
                new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId())
                        .orderByAsc(SalesDeliveryLineEntity::getLineNo)
        );
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        LocalDateTime now = audit.now();
        AccumulatedQuantityValidator deliveryQtyValidator =
                new AccumulatedQuantityValidator("出库数量超过销售订单剩余可出库数量");
        AccumulatedQuantityValidator inventoryQtyValidator =
                new AccumulatedQuantityValidator(OUTBOUND_SHORTAGE_MESSAGE);
        productValidator.requireProducts(
                deliveryLines.stream().map(SalesDeliveryLineEntity::getProductId).toList(),
                audit.companyId(),
                audit.accountBookId()
        );

        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, deliveryLine.getOrderLineId());
            BigDecimal qty = ScalePrecision.quantity(deliveryLine.getQty());
            deliveryQtyValidator.ensureWithinLimit(orderLine.getId(), qty, availableDeliveryQty(orderLine));
            inventoryQtyValidator.ensureWithinLimit(
                    deliveryLine.getProductId(),
                    qty,
                    productId -> inventoryPostingService.getQtyOnHand(
                            delivery.getWarehouseId(),
                            productId,
                            audit.companyId(),
                            audit.accountBookId()
                    )
            );
        }
        validateReservationForPosting(deliveryLines, audit.companyId(), audit.accountBookId());

        // 出库质检闸门：需检验商品必须存在已判定 OQC 检验单，且出库数量=合格数量。
        qcInspectionGate.assertDeliveryInspected(delivery, deliveryLines, audit);

        delivery.setStatus("POSTED");
        delivery.setUpdatedBy(audit.userId());
        delivery.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesDeliveryMapper.updateById(delivery),
                "销售出库单已被其他操作修改，请刷新后重试"
        );

        BigDecimal totalCostAmount = BigDecimal.ZERO;
        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, deliveryLine.getOrderLineId());
            orderLine.setDeliveredQty(ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(orderLine.getDeliveredQty())
                            .add(ScalePrecision.quantity(deliveryLine.getQty()))
            ));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    salesOrderLineMapper.updateById(orderLine),
                    "销售订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.releaseReservation(
                    "SALES_ORDER",
                    deliveryLine.getOrderLineId(),
                    deliveryLine.getQty(),
                    audit
            );
            BigDecimal lineCostAmount = inventoryPostingService.postOutbound(
                    new InventoryPostingCommand(
                            delivery.getWarehouseId(),
                            deliveryLine.getProductId(),
                            "SALES_DELIVERY",
                            delivery.getDeliveryNo(),
                            deliveryLine.getId(),
                            deliveryLine.getQty(),
                            deliveryLine.getAmount(),
                            deliveryLine.getRemark(),
                            delivery.getDeliveryDate(),
                            deliveryLine.getLotNo(),
                            deliveryLine.getProductionDate(),
                            deliveryLine.getExpiryDate(),
                            deliveryLine.getLocationId()
                    ),
                    audit,
                    OUTBOUND_SHORTAGE_MESSAGE
            );
            inventorySerialNumberService.issueOutboundSerials(
                    deliveryLine.getProductId(),
                    deliveryLine.getSerialNos(),
                    "SALES_DELIVERY",
                    delivery.getDeliveryNo(),
                    deliveryLine.getQty(),
                    audit
            );
            totalCostAmount = ScalePrecision.amount(totalCostAmount.add(lineCostAmount));
        }

        refreshDeliveryStatus(order.getId(), audit, now);
        financePostingService.recordSalesDelivery(delivery, order, totalCostAmount, audit);
        return salesDeliveryQueryService.getById(id);
    }

    private SalesOrderEntity requireApprovedOrder(Long id, String message) {
        SalesOrderEntity order = requireOrder(id);
        if (!"APPROVED".equals(order.getStatus()) || !"APPROVED".equals(order.getApprovalStatus())) {
            throw new IllegalArgumentException(message);
        }
        return order;
    }

    private SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity order = salesOrderMapper.selectById(id);
        if (order == null || order.getDeletedFlag() == null || order.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        return order;
    }

    private SalesDeliveryEntity requireDelivery(Long id) {
        SalesDeliveryEntity delivery = salesDeliveryMapper.selectById(id);
        if (delivery == null || delivery.getDeletedFlag() == null || delivery.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售出库单不存在");
        }
        return delivery;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private void assertWarehouseMatchesOrder(Long deliveryWarehouseId, Long orderWarehouseId) {
        if (!Objects.equals(deliveryWarehouseId, orderWarehouseId)) {
            throw new IllegalArgumentException("销售出库仓库必须与销售订单预占仓库一致");
        }
    }

    private Map<Long, SalesOrderLineEntity> loadOrderLinesAsMap(SalesOrderEntity order) {
        return salesOrderLineMapper.selectList(
                        new LambdaQueryWrapper<SalesOrderLineEntity>()
                                .eq(SalesOrderLineEntity::getCompanyId, order.getCompanyId())
                                .eq(SalesOrderLineEntity::getAccountBookId, order.getAccountBookId())
                                .eq(SalesOrderLineEntity::getOrderId, order.getId())
                )
                .stream()
                .collect(Collectors.toMap(SalesOrderLineEntity::getId, Function.identity()));
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

    private void validateReservationForPosting(
            List<SalesDeliveryLineEntity> deliveryLines,
            Long companyId,
            Long accountBookId
    ) {
        AccumulatedQuantityValidator reservationValidator =
                new AccumulatedQuantityValidator(POST_RESERVATION_SHORTAGE_MESSAGE);
        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            reservationValidator.ensureWithinLimit(
                    deliveryLine.getOrderLineId(),
                    ScalePrecision.quantity(deliveryLine.getQty()),
                    orderLineId -> activeReservationRemainingQty(orderLineId, companyId, accountBookId)
            );
        }
    }

    private BigDecimal activeReservationRemainingQty(Long orderLineId, Long companyId, Long accountBookId) {
        return inventoryReservationMapper.selectList(
                        new LambdaQueryWrapper<InventoryReservationEntity>()
                                .eq(InventoryReservationEntity::getCompanyId, companyId)
                                .eq(InventoryReservationEntity::getAccountBookId, accountBookId)
                                .eq(InventoryReservationEntity::getSourceType, "SALES_ORDER")
                                .eq(InventoryReservationEntity::getSourceLineId, orderLineId)
                                .eq(InventoryReservationEntity::getStatus, "ACTIVE")
                )
                .stream()
                .map(InventoryReservationEntity::getRemainingQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private void refreshDeliveryStatus(Long orderId, AuditMetadata audit, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_STATUS_REFRESH_ATTEMPTS; attempt++) {
            SalesOrderEntity order = requireOrder(orderId);
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
            BigDecimal deliveredQty = ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(orderLine.getDeliveredQty())
            );
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

    private BigDecimal availableDeliveryQty(SalesOrderLineEntity orderLine) {
        return ScalePrecision.quantity(
                ScalePrecision.quantity(orderLine.getQty())
                        .subtract(ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getDeliveredQty())))
        );
    }
}
