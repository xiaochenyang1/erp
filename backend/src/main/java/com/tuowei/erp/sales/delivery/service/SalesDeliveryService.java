package com.tuowei.erp.sales.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLineRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLineResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.support.SalesAmountCalculator;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesDeliveryService {

    private static final String OUTBOUND_SHORTAGE_MESSAGE = "库存不足，不能执行销售出库";
    private static final String CREATE_RESERVATION_SHORTAGE_MESSAGE = "销售订单预占数量不足，不能创建销售出库单";
    private static final String POST_RESERVATION_SHORTAGE_MESSAGE = "销售订单预占数量不足，不能执行销售出库";

    private static final int MAX_STATUS_REFRESH_ATTEMPTS = 8;

    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final InventoryReservationMapper inventoryReservationMapper;
    private final InventoryPostingService inventoryPostingService;
    private final SalesDeliveryNumberService salesDeliveryNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final AccountPeriodGuard accountPeriodGuard;
    private final ProductValidator productValidator;
    private final QcInspectionGate qcInspectionGate;

    public SalesDeliveryService(
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            InventoryReservationMapper inventoryReservationMapper,
            InventoryPostingService inventoryPostingService,
            SalesDeliveryNumberService salesDeliveryNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            AccountPeriodGuard accountPeriodGuard,
            ProductValidator productValidator,
            QcInspectionGate qcInspectionGate
    ) {
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.inventoryReservationMapper = inventoryReservationMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.salesDeliveryNumberService = salesDeliveryNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.accountPeriodGuard = accountPeriodGuard;
        this.productValidator = productValidator;
        this.qcInspectionGate = qcInspectionGate;
    }

    @Transactional
    public SalesDeliveryResponse create(SalesDeliveryCreateRequest request) {
        SalesOrderEntity order = requireApprovedOrder(request.orderId(), "销售订单未审批通过，不能创建销售出库单");
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        assertWarehouseMatchesOrder(request.warehouseId(), order.getWarehouseId());
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        DeliveryTotals totals = calculateTotals(
                request.lines(),
                orderLines,
                order.getId(),
                null,
                audit.companyId(),
                audit.accountBookId(),
                CREATE_RESERVATION_SHORTAGE_MESSAGE
        );
        LocalDateTime now = audit.now();

        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setCompanyId(audit.companyId());
        delivery.setAccountBookId(audit.accountBookId());
        delivery.setDeliveryNo(salesDeliveryNumberService.nextDeliveryNo(request.deliveryDate()));
        delivery.setOrderId(order.getId());
        delivery.setWarehouseId(request.warehouseId());
        delivery.setDeliveryDate(request.deliveryDate());
        delivery.setStatus("DRAFT");
        delivery.setTotalQuantity(totals.totalQuantity());
        delivery.setTotalAmount(totals.totalAmount());
        delivery.setTotalTaxAmount(totals.totalTaxAmount());
        delivery.setDeletedFlag(0);
        delivery.setRemark(request.remark());
        delivery.setCarrierName(request.carrierName());
        delivery.setTrackingNo(request.trackingNo());
        delivery.setCreatedBy(audit.userId());
        delivery.setCreatedTime(now);
        delivery.setUpdatedBy(audit.userId());
        delivery.setUpdatedTime(now);
        delivery.setVersion(0);
        assertCanView(delivery);
        salesDeliveryMapper.insert(delivery);

        List<SalesDeliveryLineEntity> lines = saveDeliveryLines(delivery.getId(), request.lines(), orderLines, audit, now);
        return toResponse(delivery, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDeliveryResponse> list(SalesDeliveryPageQuery query) {
        SalesDeliveryPageQuery safeQuery = query == null ? new SalesDeliveryPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        LambdaQueryWrapper<SalesDeliveryEntity> wrapper = buildListQuery(
                keyword,
                safeQuery.getOrderId(),
                safeQuery.getWarehouseId(),
                status,
                safeQuery.getDeliveryDateFrom(),
                safeQuery.getDeliveryDateTo()
        );
        wrapper = dataScopeService.applySalesDeliveryScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<SalesDeliveryEntity> result = salesDeliveryMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SalesDeliveryResponse getById(Long id) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        assertCanView(delivery);
        List<SalesDeliveryLineEntity> lines = salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                .eq(SalesDeliveryLineEntity::getDeliveryId, id)
                .orderByAsc(SalesDeliveryLineEntity::getLineNo));
        return toResponse(delivery, lines);
    }

    @Transactional
    public SalesDeliveryResponse update(Long id, SalesDeliveryUpdateRequest request) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        assertCanView(delivery);
        if (!"DRAFT".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("当前销售出库单状态不允许编辑");
        }

        SalesOrderEntity order = requireApprovedOrder(request.orderId(), "销售订单未审批通过，不能创建销售出库单");
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        assertWarehouseMatchesOrder(request.warehouseId(), order.getWarehouseId());
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        DeliveryTotals totals = calculateTotals(
                request.lines(),
                orderLines,
                order.getId(),
                delivery.getId(),
                audit.companyId(),
                audit.accountBookId(),
                CREATE_RESERVATION_SHORTAGE_MESSAGE
        );
        LocalDateTime now = audit.now();

        delivery.setOrderId(order.getId());
        delivery.setWarehouseId(request.warehouseId());
        delivery.setDeliveryDate(request.deliveryDate());
        delivery.setTotalQuantity(totals.totalQuantity());
        delivery.setTotalAmount(totals.totalAmount());
        delivery.setTotalTaxAmount(totals.totalTaxAmount());
        delivery.setRemark(request.remark());
        delivery.setCarrierName(request.carrierName());
        delivery.setTrackingNo(request.trackingNo());
        delivery.setUpdatedBy(audit.userId());
        delivery.setUpdatedTime(now);
        assertCanView(delivery);
        OptimisticLockGuard.requireUpdated(
                salesDeliveryMapper.updateById(delivery),
                "销售出库单已被其他操作修改，请刷新后重试"
        );

        salesDeliveryLineMapper.delete(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId()));
        List<SalesDeliveryLineEntity> lines = saveDeliveryLines(delivery.getId(), request.lines(), orderLines, audit, now);
        return toResponse(delivery, lines);
    }

    @Transactional
    public SalesDeliveryResponse cancel(Long id) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        assertCanView(delivery);
        if (!"DRAFT".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("当前销售出库单状态不允许作废");
        }
        touch(delivery);
        delivery.setStatus("CANCELLED");
        OptimisticLockGuard.requireUpdated(
                salesDeliveryMapper.updateById(delivery),
                "销售出库单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public SalesDeliveryResponse post(Long id) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        assertCanView(delivery);
        if (!"DRAFT".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("当前销售出库单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(delivery.getDeliveryDate(), "销售出库过账");

        SalesOrderEntity order = requireApprovedOrder(delivery.getOrderId(), "销售订单未审批通过，不能执行出库过账");
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(delivery.getWarehouseId(), audit.companyId(), audit.accountBookId());
        assertWarehouseMatchesOrder(delivery.getWarehouseId(), order.getWarehouseId());
        List<SalesDeliveryLineEntity> deliveryLines = salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId())
                .orderByAsc(SalesDeliveryLineEntity::getLineNo));
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        LocalDateTime now = audit.now();
        AccumulatedQuantityValidator deliveryQtyValidator = new AccumulatedQuantityValidator("出库数量超过销售订单剩余可出库数量");
        AccumulatedQuantityValidator inventoryQtyValidator = new AccumulatedQuantityValidator(OUTBOUND_SHORTAGE_MESSAGE);
        productValidator.requireProducts(
                deliveryLines.stream().map(SalesDeliveryLineEntity::getProductId).toList(),
                audit.companyId(), audit.accountBookId());

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
                    ScalePrecision.zeroDefault(orderLine.getDeliveredQty()).add(ScalePrecision.quantity(deliveryLine.getQty()))
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
                            deliveryLine.getExpiryDate()
                    ),
                    audit,
                    OUTBOUND_SHORTAGE_MESSAGE
            );
            totalCostAmount = ScalePrecision.amount(totalCostAmount.add(lineCostAmount));
        }

        refreshDeliveryStatus(order.getId(), audit, now);
        financePostingService.recordSalesDelivery(delivery, order, totalCostAmount, audit);
        return getById(id);
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
        return salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                        .eq(SalesOrderLineEntity::getCompanyId, order.getCompanyId())
                        .eq(SalesOrderLineEntity::getAccountBookId, order.getAccountBookId())
                        .eq(SalesOrderLineEntity::getOrderId, order.getId()))
                .stream()
                .collect(Collectors.toMap(SalesOrderLineEntity::getId, Function.identity()));
    }

    private SalesOrderLineEntity requireOrderLine(Map<Long, SalesOrderLineEntity> orderLines, Long orderLineId) {
        SalesOrderLineEntity orderLine = orderLines.get(orderLineId);
        if (orderLine == null) {
            throw new IllegalArgumentException("销售订单明细不存在");
        }
        return orderLine;
    }

    private DeliveryTotals calculateTotals(
            List<SalesDeliveryLineRequest> lines,
            Map<Long, SalesOrderLineEntity> orderLines,
            Long orderId,
            Long excludeDeliveryId,
            Long companyId,
            Long accountBookId,
            String reservationShortageMessage
    ) {
        SalesAmountCalculator.DocumentTotals totals = SalesAmountCalculator.DocumentTotals.zero();
        AccumulatedQuantityValidator quantityValidator = new AccumulatedQuantityValidator("出库数量超过销售订单剩余可出库数量");
        AccumulatedQuantityValidator reservationValidator = new AccumulatedQuantityValidator(reservationShortageMessage);
        Map<Long, BigDecimal> occupiedDraftQtyByOrderLineId = occupiedDraftQtyByOrderLineId(
                orderId,
                orderLines.keySet(),
                excludeDeliveryId,
                companyId,
                accountBookId
        );

        for (SalesDeliveryLineRequest line : lines) {
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, line.orderLineId());
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    line.qty(),
                    orderLine.getPrice(),
                    orderLine.getTaxRate()
            );
            quantityValidator.ensureWithinLimit(orderLine.getId(), amounts.qty(), availableDeliveryQty(orderLine));
            reservationValidator.ensureWithinLimit(
                    orderLine.getId(),
                    amounts.qty(),
                    orderLineId -> availableReservedQty(orderLineId, companyId, accountBookId, occupiedDraftQtyByOrderLineId)
            );
            totals = totals.add(amounts);
        }
        return new DeliveryTotals(totals.totalQuantity(), totals.totalAmount(), totals.totalTaxAmount());
    }

    private void validateReservationForPosting(List<SalesDeliveryLineEntity> deliveryLines, Long companyId, Long accountBookId) {
        AccumulatedQuantityValidator reservationValidator = new AccumulatedQuantityValidator(POST_RESERVATION_SHORTAGE_MESSAGE);
        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            reservationValidator.ensureWithinLimit(
                    deliveryLine.getOrderLineId(),
                    ScalePrecision.quantity(deliveryLine.getQty()),
                    orderLineId -> activeReservationRemainingQty(orderLineId, companyId, accountBookId)
            );
        }
    }

    private BigDecimal availableReservedQty(
            Long orderLineId,
            Long companyId,
            Long accountBookId,
            Map<Long, BigDecimal> occupiedDraftQtyByOrderLineId
    ) {
        BigDecimal remainingQty = activeReservationRemainingQty(orderLineId, companyId, accountBookId);
        BigDecimal occupiedQty = ScalePrecision.quantity(
                occupiedDraftQtyByOrderLineId.getOrDefault(orderLineId, BigDecimal.ZERO)
        );
        BigDecimal availableQty = ScalePrecision.quantity(remainingQty.subtract(occupiedQty));
        if (availableQty.compareTo(BigDecimal.ZERO) < 0) {
            return ScalePrecision.quantity(BigDecimal.ZERO);
        }
        return availableQty;
    }

    private BigDecimal activeReservationRemainingQty(Long orderLineId, Long companyId, Long accountBookId) {
        return inventoryReservationMapper.selectList(new LambdaQueryWrapper<InventoryReservationEntity>()
                        .eq(InventoryReservationEntity::getCompanyId, companyId)
                        .eq(InventoryReservationEntity::getAccountBookId, accountBookId)
                        .eq(InventoryReservationEntity::getSourceType, "SALES_ORDER")
                        .eq(InventoryReservationEntity::getSourceLineId, orderLineId)
                        .eq(InventoryReservationEntity::getStatus, "ACTIVE"))
                .stream()
                .map(InventoryReservationEntity::getRemainingQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> occupiedDraftQtyByOrderLineId(
            Long orderId,
            Set<Long> orderLineIds,
            Long excludeDeliveryId,
            Long companyId,
            Long accountBookId
    ) {
        if (orderId == null || orderLineIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SalesDeliveryEntity> draftDeliveryQuery = new LambdaQueryWrapper<SalesDeliveryEntity>()
                .eq(SalesDeliveryEntity::getCompanyId, companyId)
                .eq(SalesDeliveryEntity::getAccountBookId, accountBookId)
                .eq(SalesDeliveryEntity::getOrderId, orderId)
                .eq(SalesDeliveryEntity::getStatus, "DRAFT")
                .eq(SalesDeliveryEntity::getDeletedFlag, 0);
        if (excludeDeliveryId != null) {
            draftDeliveryQuery.ne(SalesDeliveryEntity::getId, excludeDeliveryId);
        }
        List<Long> draftDeliveryIds = salesDeliveryMapper.selectList(draftDeliveryQuery)
                .stream()
                .map(SalesDeliveryEntity::getId)
                .toList();
        if (draftDeliveryIds.isEmpty()) {
            return Map.of();
        }
        return salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, companyId)
                        .eq(SalesDeliveryLineEntity::getAccountBookId, accountBookId)
                        .in(SalesDeliveryLineEntity::getDeliveryId, draftDeliveryIds)
                        .in(SalesDeliveryLineEntity::getOrderLineId, orderLineIds))
                .stream()
                .collect(Collectors.toMap(
                        SalesDeliveryLineEntity::getOrderLineId,
                        line -> ScalePrecision.quantity(line.getQty()),
                        (left, right) -> ScalePrecision.quantity(left.add(right))
                ));
    }

    private List<SalesDeliveryLineEntity> saveDeliveryLines(
            Long deliveryId,
            List<SalesDeliveryLineRequest> requests,
            Map<Long, SalesOrderLineEntity> orderLines,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<SalesDeliveryLineEntity> deliveryLines = new ArrayList<>();
        productValidator.requireProducts(
                requests.stream()
                        .map(r -> requireOrderLine(orderLines, r.orderLineId()).getProductId())
                        .toList(),
                audit.companyId(), audit.accountBookId());
        for (int i = 0; i < requests.size(); i++) {
            SalesDeliveryLineRequest request = requests.get(i);
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, request.orderLineId());
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    request.qty(),
                    orderLine.getPrice(),
                    orderLine.getTaxRate()
            );

            SalesDeliveryLineEntity deliveryLine = new SalesDeliveryLineEntity();
            deliveryLine.setCompanyId(audit.companyId());
            deliveryLine.setAccountBookId(audit.accountBookId());
            deliveryLine.setDeliveryId(deliveryId);
            deliveryLine.setLineNo(i + 1);
            deliveryLine.setOrderLineId(orderLine.getId());
            deliveryLine.setProductId(orderLine.getProductId());
            deliveryLine.setQty(amounts.qty());
            deliveryLine.setPrice(amounts.price());
            deliveryLine.setTaxRate(amounts.taxRate());
            deliveryLine.setAmount(amounts.amount());
            deliveryLine.setTaxAmount(amounts.taxAmount());
            deliveryLine.setReturnedQty(ScalePrecision.quantity(BigDecimal.ZERO));
            deliveryLine.setLotNo(request.lotNo());
            deliveryLine.setProductionDate(request.productionDate());
            deliveryLine.setExpiryDate(request.expiryDate());
            deliveryLine.setRemark(request.remark());
            deliveryLine.setCreatedBy(audit.userId());
            deliveryLine.setCreatedTime(now);
            deliveryLine.setUpdatedBy(audit.userId());
            deliveryLine.setUpdatedTime(now);
            deliveryLine.setVersion(0);
            salesDeliveryLineMapper.insert(deliveryLine);
            deliveryLines.add(deliveryLine);
        }
        return deliveryLines;
    }

    private LambdaQueryWrapper<SalesDeliveryEntity> buildListQuery(
            String keyword,
            Long orderId,
            Long warehouseId,
            String status,
            LocalDate deliveryDateFrom,
            LocalDate deliveryDateTo
    ) {
        LambdaQueryWrapper<SalesDeliveryEntity> wrapper = new LambdaQueryWrapper<SalesDeliveryEntity>()
                .eq(SalesDeliveryEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SalesDeliveryEntity::getDeliveryNo, keyword);
        }
        if (orderId != null) {
            wrapper.eq(SalesDeliveryEntity::getOrderId, orderId);
        }
        if (warehouseId != null) {
            wrapper.eq(SalesDeliveryEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesDeliveryEntity::getStatus, status);
        }
        if (deliveryDateFrom != null) {
            wrapper.ge(SalesDeliveryEntity::getDeliveryDate, deliveryDateFrom);
        }
        if (deliveryDateTo != null) {
            wrapper.le(SalesDeliveryEntity::getDeliveryDate, deliveryDateTo);
        }
        return wrapper.orderByDesc(SalesDeliveryEntity::getId);
    }

    private void assertCanView(SalesDeliveryEntity delivery) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = delivery.getCreatedBy() == null ? null : userMapper.selectById(delivery.getCreatedBy());
        dataScopeService.assertCanViewSalesDelivery(
                delivery,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(SalesOrderEntity order) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = order.getCreatedBy() == null ? null : userMapper.selectById(order.getCreatedBy());
        dataScopeService.assertCanViewSalesOrder(
                order,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
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

    private SalesDeliveryResponse toResponse(SalesDeliveryEntity delivery, List<SalesDeliveryLineEntity> lines) {
        return new SalesDeliveryResponse(
                delivery.getId(),
                delivery.getDeliveryNo(),
                delivery.getOrderId(),
                delivery.getWarehouseId(),
                delivery.getDeliveryDate(),
                delivery.getStatus(),
                delivery.getTotalQuantity(),
                delivery.getTotalAmount(),
                delivery.getTotalTaxAmount(),
                delivery.getRemark(),
                delivery.getCarrierName(),
                delivery.getTrackingNo(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private SalesDeliveryResponse toSummaryResponse(SalesDeliveryEntity delivery) {
        return new SalesDeliveryResponse(
                delivery.getId(),
                delivery.getDeliveryNo(),
                delivery.getOrderId(),
                delivery.getWarehouseId(),
                delivery.getDeliveryDate(),
                delivery.getStatus(),
                delivery.getTotalQuantity(),
                delivery.getTotalAmount(),
                delivery.getTotalTaxAmount(),
                delivery.getRemark(),
                delivery.getCarrierName(),
                delivery.getTrackingNo(),
                List.of()
        );
    }

    private SalesDeliveryLineResponse toLineResponse(SalesDeliveryLineEntity line) {
        return new SalesDeliveryLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getOrderLineId(),
                line.getProductId(),
                line.getQty(),
                line.getPrice(),
                line.getTaxRate(),
                line.getAmount(),
                line.getTaxAmount(),
                line.getReturnedQty(),
                line.getLotNo(),
                line.getProductionDate(),
                line.getExpiryDate(),
                line.getRemark()
        );
    }

    private BigDecimal availableDeliveryQty(SalesOrderLineEntity orderLine) {
        return ScalePrecision.quantity(
                ScalePrecision.quantity(orderLine.getQty())
                        .subtract(ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getDeliveredQty())))
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private void touch(SalesDeliveryEntity delivery) {
        AuditMetadata audit = auditMetadataFactory.current();
        delivery.setUpdatedBy(audit.userId());
        delivery.setUpdatedTime(audit.now());
    }

    private record DeliveryTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }
}
