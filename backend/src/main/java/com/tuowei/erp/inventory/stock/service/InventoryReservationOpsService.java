package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationDetailResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationEventResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationManualReleaseRequest;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryResponse;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReservationOpsService {

    private final InventoryReservationMapper reservationMapper;
    private final InventoryReservationEventMapper reservationEventMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final SystemLogService systemLogService;

    public InventoryReservationOpsService(
            InventoryReservationMapper reservationMapper,
            InventoryReservationEventMapper reservationEventMapper,
            InventoryBalanceMapper balanceMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            SystemLogService systemLogService
    ) {
        this.reservationMapper = reservationMapper;
        this.reservationEventMapper = reservationEventMapper;
        this.balanceMapper = balanceMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.systemLogService = systemLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReservationResponse> listReservations(InventoryReservationPageQuery query) {
        InventoryReservationPageQuery safeQuery = query == null ? new InventoryReservationPageQuery() : query;
        Page<InventoryReservationEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = buildReservationQuery(safeQuery);
        wrapper = dataScopeService.applyInventoryReservationScope(wrapper, currentSnapshot());
        Page<InventoryReservationEntity> result = reservationMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toReservationResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public InventoryReservationDetailResponse getReservation(Long id) {
        InventoryReservationEntity reservation = requireReservation(id);
        dataScopeService.assertCanViewInventoryReservation(reservation, currentSnapshot());
        return toDetailResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationSummaryResponse> summary(InventoryReservationSummaryQuery query) {
        InventoryReservationSummaryQuery safeQuery = query == null ? new InventoryReservationSummaryQuery() : query;
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                dataScopeService.applyInventoryReservationScope(buildSummaryReservationQuery(safeQuery), currentSnapshot())
        );
        Map<BalanceKey, InventoryBalanceEntity> balances = balanceMapper.selectList(
                        dataScopeService.applyInventoryBalanceScope(buildBalanceQuery(safeQuery.getWarehouseId(), safeQuery.getProductId()), currentSnapshot()))
                .stream()
                .collect(Collectors.toMap(
                        balance -> new BalanceKey(balance.getWarehouseId(), balance.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<SummaryKey, SummaryAccumulator> summaries = new LinkedHashMap<>();
        for (InventoryReservationEntity reservation : reservations) {
            SummaryKey key = new SummaryKey(
                    reservation.getWarehouseId(),
                    reservation.getProductId(),
                    normalizeUpper(reservation.getSourceType()),
                    normalizeUpper(reservation.getStatus())
            );
            summaries.computeIfAbsent(key, SummaryAccumulator::new).add(reservation);
        }

        return summaries.values().stream()
                .map(accumulator -> accumulator.toResponse(balances.get(new BalanceKey(accumulator.key.warehouseId(), accumulator.key.productId()))))
                .sorted(Comparator.comparing(InventoryReservationSummaryResponse::warehouseId)
                        .thenComparing(InventoryReservationSummaryResponse::productId)
                        .thenComparing(InventoryReservationSummaryResponse::sourceType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(InventoryReservationSummaryResponse::status, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryReservationSourceResponse source(InventoryReservationSourceQuery query) {
        InventoryReservationSourceQuery safeQuery = query == null ? new InventoryReservationSourceQuery() : query;
        CurrentUser user = currentUser();
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, user.companyId())
                .eq(InventoryReservationEntity::getAccountBookId, user.accountBookId());
        String sourceType = normalizeUpper(safeQuery.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(InventoryReservationEntity::getSourceType, sourceType);
        }
        if (safeQuery.getSourceId() != null) {
            wrapper.eq(InventoryReservationEntity::getSourceId, safeQuery.getSourceId());
        }
        String sourceNo = normalizeNullableText(safeQuery.getSourceNo());
        if (StringUtils.hasText(sourceNo)) {
            wrapper.eq(InventoryReservationEntity::getSourceNo, sourceNo);
        }
        if (!StringUtils.hasText(sourceType) && safeQuery.getSourceId() == null && !StringUtils.hasText(sourceNo)) {
            throw new IllegalArgumentException("预占来源查询条件不能为空");
        }
        wrapper = dataScopeService.applyInventoryReservationScope(wrapper, currentSnapshot());
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(wrapper.orderByAsc(InventoryReservationEntity::getId));
        String responseSourceType = reservations.isEmpty() ? sourceType : reservations.get(0).getSourceType();
        Long responseSourceId = reservations.isEmpty() ? safeQuery.getSourceId() : reservations.get(0).getSourceId();
        String responseSourceNo = reservations.isEmpty() ? sourceNo : reservations.get(0).getSourceNo();
        return new InventoryReservationSourceResponse(
                responseSourceType,
                responseSourceId,
                responseSourceNo,
                reservations.stream().map(this::toDetailResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationCheckIssueResponse> checks(InventoryReservationCheckQuery query) {
        InventoryReservationCheckQuery safeQuery = query == null ? new InventoryReservationCheckQuery() : query;
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                dataScopeService.applyInventoryReservationScope(buildCheckReservationQuery(safeQuery), currentSnapshot())
        );
        List<InventoryBalanceEntity> balances = balanceMapper.selectList(
                dataScopeService.applyInventoryBalanceScope(buildBalanceQuery(safeQuery.getWarehouseId(), safeQuery.getProductId()), currentSnapshot())
        );
        Map<BalanceKey, InventoryBalanceEntity> balanceByKey = balances.stream()
                .collect(Collectors.toMap(
                        balance -> new BalanceKey(balance.getWarehouseId(), balance.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<BalanceKey, BigDecimal> activeRemainingByBalance = new LinkedHashMap<>();
        List<InventoryReservationCheckIssueResponse> issues = new ArrayList<>();

        for (InventoryReservationEntity reservation : reservations) {
            validateReservationQuantities(reservation, issues);
            BalanceKey key = new BalanceKey(reservation.getWarehouseId(), reservation.getProductId());
            if ("ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
                activeRemainingByBalance.merge(key, quantity(reservation.getRemainingQty()), BigDecimal::add);
            }
            if (!balanceByKey.containsKey(key)) {
                issues.add(issue("RESERVATION_BALANCE_MISSING", "ERROR", reservation, null, null, "库存预占对应的库存余额不存在"));
            }
            validateSource(reservation, issues);
        }

        for (InventoryBalanceEntity balance : balances) {
            BalanceKey key = new BalanceKey(balance.getWarehouseId(), balance.getProductId());
            BigDecimal expectedReserved = quantity(activeRemainingByBalance.getOrDefault(key, BigDecimal.ZERO));
            BigDecimal actualReserved = quantity(balance.getQtyReserved());
            if (expectedReserved.compareTo(actualReserved) != 0) {
                issues.add(new InventoryReservationCheckIssueResponse(
                        "BALANCE_RESERVED_MISMATCH",
                        "ERROR",
                        null,
                        balance.getWarehouseId(),
                        balance.getProductId(),
                        null,
                        null,
                        null,
                        expectedReserved,
                        actualReserved,
                        "库存余额预占数量与有效预占汇总不一致"
                ));
            }
            BigDecimal qtyAvailable = quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).subtract(ScalePrecision.zeroDefault(balance.getQtyReserved())));
            if (qtyAvailable.compareTo(BigDecimal.ZERO) < 0) {
                issues.add(new InventoryReservationCheckIssueResponse(
                        "BALANCE_AVAILABLE_NEGATIVE",
                        "ERROR",
                        null,
                        balance.getWarehouseId(),
                        balance.getProductId(),
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        qtyAvailable,
                        "库存可用量为负数"
                ));
            }
        }

        return issues;
    }

    @Transactional
    public InventoryReservationDetailResponse manualRelease(Long id, InventoryReservationManualReleaseRequest request) {
        InventoryReservationEntity reservation = requireReservation(id);
        dataScopeService.assertCanViewInventoryReservation(reservation, currentSnapshot());
        validateDraftDeliveryCoverage(reservation, quantity(request.qty()));
        AuditMetadata audit = auditMetadataFactory.current();
        inventoryPostingService.manualReleaseReservation(id, request.qty(), audit, request.reason());
        CurrentUser operator = currentUser();
        systemLogService.recordAudit(
                "INVENTORY_RESERVATION",
                "INVENTORY_RESERVATION",
                reservation.getId(),
                reservation.getSourceNo(),
                "MANUAL_RELEASE",
                operator.userId(),
                operator.username(),
                "{\"qty\":\"" + quantity(request.qty()).toPlainString() + "\"}",
                request.reason(),
                audit.now()
        );
        return toDetailResponse(requireReservation(id));
    }

    private LambdaQueryWrapper<InventoryReservationEntity> buildReservationQuery(InventoryReservationPageQuery query) {
        CurrentUser user = currentUser();
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, user.companyId())
                .eq(InventoryReservationEntity::getAccountBookId, user.accountBookId());
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryReservationEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryReservationEntity::getProductId, query.getProductId());
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(InventoryReservationEntity::getSourceType, sourceType);
        }
        String sourceNo = normalizeNullableText(query.getSourceNo());
        if (StringUtils.hasText(sourceNo)) {
            wrapper.like(InventoryReservationEntity::getSourceNo, sourceNo);
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(InventoryReservationEntity::getStatus, status);
        }
        if (query.getCreatedTimeFrom() != null) {
            wrapper.ge(InventoryReservationEntity::getCreatedTime, query.getCreatedTimeFrom());
        }
        if (query.getCreatedTimeTo() != null) {
            wrapper.le(InventoryReservationEntity::getCreatedTime, query.getCreatedTimeTo());
        }
        return wrapper.orderByDesc(InventoryReservationEntity::getUpdatedTime).orderByDesc(InventoryReservationEntity::getId);
    }

    private LambdaQueryWrapper<InventoryReservationEntity> buildSummaryReservationQuery(InventoryReservationSummaryQuery query) {
        CurrentUser user = currentUser();
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, user.companyId())
                .eq(InventoryReservationEntity::getAccountBookId, user.accountBookId());
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryReservationEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryReservationEntity::getProductId, query.getProductId());
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(InventoryReservationEntity::getSourceType, sourceType);
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(InventoryReservationEntity::getStatus, status);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<InventoryReservationEntity> buildCheckReservationQuery(InventoryReservationCheckQuery query) {
        CurrentUser user = currentUser();
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, user.companyId())
                .eq(InventoryReservationEntity::getAccountBookId, user.accountBookId());
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryReservationEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryReservationEntity::getProductId, query.getProductId());
        }
        return wrapper;
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> buildBalanceQuery(Long warehouseId, Long productId) {
        CurrentUser user = currentUser();
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, user.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, user.accountBookId());
        if (warehouseId != null) {
            wrapper.eq(InventoryBalanceEntity::getWarehouseId, warehouseId);
        }
        if (productId != null) {
            wrapper.eq(InventoryBalanceEntity::getProductId, productId);
        }
        return wrapper;
    }

    private InventoryReservationEntity requireReservation(Long id) {
        InventoryReservationEntity reservation = reservationMapper.selectById(id);
        CurrentUser user = currentUser();
        if (reservation == null
                || !Objects.equals(reservation.getCompanyId(), user.companyId())
                || !Objects.equals(reservation.getAccountBookId(), user.accountBookId())) {
            throw new IllegalArgumentException("库存预占不存在");
        }
        return reservation;
    }

    private InventoryReservationDetailResponse toDetailResponse(InventoryReservationEntity reservation) {
        return new InventoryReservationDetailResponse(
                toReservationResponse(reservation),
                reservationEventMapper.selectList(new LambdaQueryWrapper<InventoryReservationEventEntity>()
                                .eq(InventoryReservationEventEntity::getCompanyId, reservation.getCompanyId())
                                .eq(InventoryReservationEventEntity::getAccountBookId, reservation.getAccountBookId())
                                .eq(InventoryReservationEventEntity::getReservationId, reservation.getId())
                                .orderByAsc(InventoryReservationEventEntity::getCreatedTime)
                                .orderByAsc(InventoryReservationEventEntity::getId))
                        .stream()
                        .map(this::toEventResponse)
                        .toList()
        );
    }

    private InventoryReservationResponse toReservationResponse(InventoryReservationEntity reservation) {
        return new InventoryReservationResponse(
                reservation.getId(),
                reservation.getWarehouseId(),
                reservation.getProductId(),
                reservation.getSourceType(),
                reservation.getSourceId(),
                reservation.getSourceNo(),
                reservation.getSourceLineId(),
                quantity(reservation.getReservedQty()),
                quantity(reservation.getReleasedQty()),
                quantity(reservation.getRemainingQty()),
                reservation.getStatus(),
                reservation.getRemark(),
                reservation.getCreatedTime(),
                reservation.getUpdatedTime()
        );
    }

    private InventoryReservationEventResponse toEventResponse(InventoryReservationEventEntity event) {
        return new InventoryReservationEventResponse(
                event.getId(),
                event.getReservationId(),
                event.getEventType(),
                quantity(event.getEventQty()),
                quantity(event.getRemainingQtyBefore()),
                quantity(event.getRemainingQtyAfter()),
                event.getReason(),
                event.getCreatedBy(),
                event.getCreatedTime()
        );
    }

    private void validateReservationQuantities(
            InventoryReservationEntity reservation,
            List<InventoryReservationCheckIssueResponse> issues
    ) {
        BigDecimal reservedQty = quantity(reservation.getReservedQty());
        BigDecimal releasedQty = quantity(reservation.getReleasedQty());
        BigDecimal remainingQty = quantity(reservation.getRemainingQty());
        if (reservedQty.compareTo(BigDecimal.ZERO) < 0
                || releasedQty.compareTo(BigDecimal.ZERO) < 0
                || remainingQty.compareTo(BigDecimal.ZERO) < 0
                || reservedQty.compareTo(quantity(releasedQty.add(remainingQty))) != 0) {
            issues.add(issue(
                    "RESERVATION_QUANTITY_INVALID",
                    "ERROR",
                    reservation,
                    reservedQty,
                    quantity(releasedQty.add(remainingQty)),
                    "库存预占数量不自洽"
            ));
        }
    }

    private void validateSource(
            InventoryReservationEntity reservation,
            List<InventoryReservationCheckIssueResponse> issues
    ) {
        if (!"SALES_ORDER".equalsIgnoreCase(reservation.getSourceType())) {
            return;
        }
        SalesOrderEntity order = salesOrderMapper.selectById(reservation.getSourceId());
        SalesOrderLineEntity line = salesOrderLineMapper.selectById(reservation.getSourceLineId());
        if (order == null
                || line == null
                || !Objects.equals(order.getCompanyId(), reservation.getCompanyId())
                || !Objects.equals(order.getAccountBookId(), reservation.getAccountBookId())
                || !Objects.equals(line.getCompanyId(), reservation.getCompanyId())
                || !Objects.equals(line.getAccountBookId(), reservation.getAccountBookId())
                || !Objects.equals(line.getOrderId(), reservation.getSourceId())) {
            issues.add(issue("RESERVATION_SOURCE_MISSING", "ERROR", reservation, null, null, "库存预占来源销售订单或明细不存在"));
            return;
        }
        boolean invalidOrderStatus = !"APPROVED".equalsIgnoreCase(order.getStatus())
                || !"APPROVED".equalsIgnoreCase(order.getApprovalStatus());
        boolean fullyDelivered = "FULL_DELIVERED".equalsIgnoreCase(order.getDeliveryStatus())
                && quantity(reservation.getRemainingQty()).compareTo(BigDecimal.ZERO) > 0;
        if (invalidOrderStatus || fullyDelivered) {
            issues.add(issue("RESERVATION_SOURCE_STATUS_INVALID", "WARN", reservation, null, null, "库存预占来源销售订单状态不匹配"));
        }
    }

    private void validateDraftDeliveryCoverage(InventoryReservationEntity reservation, BigDecimal releaseQty) {
        if (!"SALES_ORDER".equalsIgnoreCase(reservation.getSourceType())) {
            return;
        }
        BigDecimal remainingAfterRelease = quantity(quantity(reservation.getRemainingQty()).subtract(releaseQty));
        BigDecimal occupiedDraftQty = draftDeliveryQty(
                reservation.getCompanyId(),
                reservation.getAccountBookId(),
                reservation.getSourceId(),
                reservation.getSourceLineId()
        );
        if (remainingAfterRelease.compareTo(occupiedDraftQty) < 0) {
            throw new IllegalArgumentException("预占已被销售出库草稿占用，不能释放");
        }
    }

    private BigDecimal draftDeliveryQty(Long companyId, Long accountBookId, Long orderId, Long orderLineId) {
        List<Long> draftDeliveryIds = salesDeliveryMapper.selectList(new LambdaQueryWrapper<SalesDeliveryEntity>()
                        .eq(SalesDeliveryEntity::getCompanyId, companyId)
                        .eq(SalesDeliveryEntity::getAccountBookId, accountBookId)
                        .eq(SalesDeliveryEntity::getOrderId, orderId)
                        .eq(SalesDeliveryEntity::getStatus, "DRAFT")
                        .eq(SalesDeliveryEntity::getDeletedFlag, 0))
                .stream()
                .map(SalesDeliveryEntity::getId)
                .toList();
        if (draftDeliveryIds.isEmpty()) {
            return quantity(BigDecimal.ZERO);
        }
        return quantity(salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, companyId)
                        .eq(SalesDeliveryLineEntity::getAccountBookId, accountBookId)
                        .in(SalesDeliveryLineEntity::getDeliveryId, draftDeliveryIds)
                        .eq(SalesDeliveryLineEntity::getOrderLineId, orderLineId))
                .stream()
                .map(SalesDeliveryLineEntity::getQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private InventoryReservationCheckIssueResponse issue(
            String issueType,
            String severity,
            InventoryReservationEntity reservation,
            BigDecimal expectedQty,
            BigDecimal actualQty,
            String message
    ) {
        return new InventoryReservationCheckIssueResponse(
                issueType,
                severity,
                reservation.getId(),
                reservation.getWarehouseId(),
                reservation.getProductId(),
                reservation.getSourceType(),
                reservation.getSourceId(),
                reservation.getSourceNo(),
                expectedQty,
                actualQty,
                message
        );
    }

    private CurrentUser currentUser() {
        return currentUserContext.requireCurrentUser();
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
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

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private BigDecimal quantity(BigDecimal value) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(value));
    }

    private record BalanceKey(Long warehouseId, Long productId) {
    }

    private record SummaryKey(Long warehouseId, Long productId, String sourceType, String status) {
    }

    private class SummaryAccumulator {

        private final SummaryKey key;
        private BigDecimal reservedQty = quantity(BigDecimal.ZERO);
        private BigDecimal releasedQty = quantity(BigDecimal.ZERO);
        private BigDecimal remainingQty = quantity(BigDecimal.ZERO);
        private long count;

        private SummaryAccumulator(SummaryKey key) {
            this.key = key;
        }

        private void add(InventoryReservationEntity reservation) {
            reservedQty = quantity(reservedQty.add(quantity(reservation.getReservedQty())));
            releasedQty = quantity(releasedQty.add(quantity(reservation.getReleasedQty())));
            remainingQty = quantity(remainingQty.add(quantity(reservation.getRemainingQty())));
            count++;
        }

        private InventoryReservationSummaryResponse toResponse(InventoryBalanceEntity balance) {
            BigDecimal qtyOnHand = balance == null ? quantity(BigDecimal.ZERO) : quantity(balance.getQtyOnHand());
            BigDecimal qtyReserved = balance == null ? quantity(BigDecimal.ZERO) : quantity(balance.getQtyReserved());
            BigDecimal qtyAvailable = quantity(qtyOnHand.subtract(qtyReserved));
            return new InventoryReservationSummaryResponse(
                    key.warehouseId(),
                    key.productId(),
                    key.sourceType(),
                    key.status(),
                    reservedQty,
                    releasedQty,
                    remainingQty,
                    qtyOnHand,
                    qtyReserved,
                    qtyAvailable,
                    count
            );
        }
    }
}
