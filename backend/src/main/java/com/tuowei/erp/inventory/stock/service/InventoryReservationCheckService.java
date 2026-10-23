package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckIssueResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReservationCheckService {

    private final InventoryReservationMapper reservationMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;

    public InventoryReservationCheckService(
            InventoryReservationMapper reservationMapper,
            InventoryBalanceMapper balanceMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService
    ) {
        this.reservationMapper = reservationMapper;
        this.balanceMapper = balanceMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationCheckIssueResponse> checks(InventoryReservationCheckQuery query) {
        InventoryReservationCheckQuery safeQuery = query == null ? new InventoryReservationCheckQuery() : query;
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                dataScopeService.applyInventoryReservationScope(
                        buildReservationQuery(safeQuery),
                        currentSnapshot()
                )
        );
        List<InventoryBalanceEntity> balances = balanceMapper.selectList(
                dataScopeService.applyInventoryBalanceScope(
                        buildBalanceQuery(safeQuery.getWarehouseId(), safeQuery.getProductId()),
                        currentSnapshot()
                )
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
                issues.add(issue(
                        "RESERVATION_BALANCE_MISSING",
                        "ERROR",
                        reservation,
                        null,
                        null,
                        "库存预占对应的库存余额不存在"
                ));
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
            BigDecimal qtyAvailable = quantity(
                    ScalePrecision.zeroDefault(balance.getQtyOnHand())
                            .subtract(ScalePrecision.zeroDefault(balance.getQtyReserved()))
            );
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

    private LambdaQueryWrapper<InventoryReservationEntity> buildReservationQuery(
            InventoryReservationCheckQuery query
    ) {
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
            issues.add(issue(
                    "RESERVATION_SOURCE_MISSING",
                    "ERROR",
                    reservation,
                    null,
                    null,
                    "库存预占来源销售订单或明细不存在"
            ));
            return;
        }
        boolean invalidOrderStatus = !"APPROVED".equalsIgnoreCase(order.getStatus())
                || !"APPROVED".equalsIgnoreCase(order.getApprovalStatus());
        boolean fullyDelivered = "FULL_DELIVERED".equalsIgnoreCase(order.getDeliveryStatus())
                && quantity(reservation.getRemainingQty()).compareTo(BigDecimal.ZERO) > 0;
        if (invalidOrderStatus || fullyDelivered) {
            issues.add(issue(
                    "RESERVATION_SOURCE_STATUS_INVALID",
                    "WARN",
                    reservation,
                    null,
                    null,
                    "库存预占来源销售订单状态不匹配"
            ));
        }
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

    private BigDecimal quantity(BigDecimal value) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(value));
    }

    private record BalanceKey(Long warehouseId, Long productId) {
    }
}
