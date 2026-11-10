package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
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
import com.tuowei.erp.inventory.stock.web.InventoryReservationDetailResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationEventResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReservationQueryService {

    private final InventoryReservationMapper reservationMapper;
    private final InventoryReservationEventMapper reservationEventMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;

    public InventoryReservationQueryService(
            InventoryReservationMapper reservationMapper,
            InventoryReservationEventMapper reservationEventMapper,
            InventoryBalanceMapper balanceMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService
    ) {
        this.reservationMapper = reservationMapper;
        this.reservationEventMapper = reservationEventMapper;
        this.balanceMapper = balanceMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReservationResponse> listReservations(InventoryReservationPageQuery query) {
        InventoryReservationPageQuery safeQuery = query == null ? new InventoryReservationPageQuery() : query;
        Page<InventoryReservationEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryReservationEntity> wrapper = dataScopeService.applyInventoryReservationScope(
                buildReservationQuery(safeQuery),
                currentSnapshot()
        );
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
        return toDetailResponse(requireVisibleReservation(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationSummaryResponse> summary(InventoryReservationSummaryQuery query) {
        InventoryReservationSummaryQuery safeQuery = query == null ? new InventoryReservationSummaryQuery() : query;
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                dataScopeService.applyInventoryReservationScope(
                        buildSummaryReservationQuery(safeQuery),
                        currentSnapshot()
                )
        );
        Map<BalanceKey, InventoryBalanceEntity> balances = balanceMapper.selectList(
                        dataScopeService.applyInventoryBalanceScope(
                                buildBalanceQuery(safeQuery.getWarehouseId(), safeQuery.getProductId()),
                                currentSnapshot()
                        ))
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
                .map(accumulator -> accumulator.toResponse(
                        balances.get(new BalanceKey(accumulator.key.warehouseId(), accumulator.key.productId()))
                ))
                .sorted(Comparator.comparing(InventoryReservationSummaryResponse::warehouseId)
                        .thenComparing(InventoryReservationSummaryResponse::productId)
                        .thenComparing(
                                InventoryReservationSummaryResponse::sourceType,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(
                                InventoryReservationSummaryResponse::status,
                                Comparator.nullsLast(String::compareTo)
                        ))
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
        if (!StringUtils.hasText(sourceType)
                && safeQuery.getSourceId() == null
                && !StringUtils.hasText(sourceNo)) {
            throw new IllegalArgumentException("预占来源查询条件不能为空");
        }
        wrapper = dataScopeService.applyInventoryReservationScope(wrapper, currentSnapshot());
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                wrapper.orderByAsc(InventoryReservationEntity::getId)
        );
        String responseSourceType = reservations.isEmpty() ? sourceType : reservations.get(0).getSourceType();
        Long responseSourceId = reservations.isEmpty() ? safeQuery.getSourceId() : reservations.get(0).getSourceId();
        String responseSourceNo = reservations.isEmpty() ? sourceNo : reservations.get(0).getSourceNo();
        Map<Long, List<InventoryReservationEventResponse>> eventsByReservationId =
                loadEventsByReservationId(reservations);
        return new InventoryReservationSourceResponse(
                responseSourceType,
                responseSourceId,
                responseSourceNo,
                reservations.stream()
                        .map(reservation -> toDetailResponse(
                                reservation,
                                eventsByReservationId.getOrDefault(reservation.getId(), List.of())
                        ))
                        .toList()
        );
    }

    InventoryReservationEntity requireVisibleReservation(Long id) {
        InventoryReservationEntity reservation = requireReservation(id);
        dataScopeService.assertCanViewInventoryReservation(reservation, currentSnapshot());
        return reservation;
    }

    private LambdaQueryWrapper<InventoryReservationEntity> buildReservationQuery(
            InventoryReservationPageQuery query
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
        return wrapper.orderByDesc(InventoryReservationEntity::getUpdatedTime)
                .orderByDesc(InventoryReservationEntity::getId);
    }

    private LambdaQueryWrapper<InventoryReservationEntity> buildSummaryReservationQuery(
            InventoryReservationSummaryQuery query
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
                loadEvents(reservation)
        );
    }

    private InventoryReservationDetailResponse toDetailResponse(
            InventoryReservationEntity reservation,
            List<InventoryReservationEventResponse> events
    ) {
        return new InventoryReservationDetailResponse(toReservationResponse(reservation), events);
    }

    private List<InventoryReservationEventResponse> loadEvents(InventoryReservationEntity reservation) {
        return reservationEventMapper.selectList(new LambdaQueryWrapper<InventoryReservationEventEntity>()
                        .eq(InventoryReservationEventEntity::getCompanyId, reservation.getCompanyId())
                        .eq(InventoryReservationEventEntity::getAccountBookId, reservation.getAccountBookId())
                        .eq(InventoryReservationEventEntity::getReservationId, reservation.getId())
                        .orderByAsc(InventoryReservationEventEntity::getCreatedTime)
                        .orderByAsc(InventoryReservationEventEntity::getId))
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    private Map<Long, List<InventoryReservationEventResponse>> loadEventsByReservationId(
            List<InventoryReservationEntity> reservations
    ) {
        if (reservations.isEmpty()) {
            return Collections.emptyMap();
        }
        InventoryReservationEntity first = reservations.get(0);
        List<Long> reservationIds = reservations.stream().map(InventoryReservationEntity::getId).toList();
        return reservationEventMapper.selectList(new LambdaQueryWrapper<InventoryReservationEventEntity>()
                        .eq(InventoryReservationEventEntity::getCompanyId, first.getCompanyId())
                        .eq(InventoryReservationEventEntity::getAccountBookId, first.getAccountBookId())
                        .in(InventoryReservationEventEntity::getReservationId, reservationIds)
                        .orderByAsc(InventoryReservationEventEntity::getCreatedTime)
                        .orderByAsc(InventoryReservationEventEntity::getId))
                .stream()
                .map(this::toEventResponse)
                .collect(Collectors.groupingBy(InventoryReservationEventResponse::reservationId));
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

    private CurrentUser currentUser() {
        return currentUserContext.requireCurrentUser();
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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
