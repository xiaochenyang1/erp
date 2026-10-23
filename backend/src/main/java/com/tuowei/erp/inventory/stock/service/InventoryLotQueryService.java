package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Service
public class InventoryLotQueryService {

    private static final int DEFAULT_EXPIRY_WARNING_DAYS = 30;
    private static final int MAX_EXPIRY_WARNING_DAYS = 365;

    private final InventoryLotBalanceMapper inventoryLotBalanceMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final InventoryDocumentLinkResolver documentLinkResolver;
    private final Clock clock;

    public InventoryLotQueryService(
            InventoryLotBalanceMapper inventoryLotBalanceMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            InventoryDocumentLinkResolver documentLinkResolver,
            Clock clock
    ) {
        this.inventoryLotBalanceMapper = inventoryLotBalanceMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.documentLinkResolver = documentLinkResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotBalanceResponse> listLotBalances(InventoryLotBalancePageQuery query) {
        InventoryLotBalancePageQuery safeQuery = query == null ? new InventoryLotBalancePageQuery() : query;
        CurrentUser user = currentUser();
        Page<InventoryLotBalanceEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = buildLotBalanceQuery(
                user.companyId(),
                user.accountBookId(),
                safeQuery
        );
        wrapper = dataScopeService.applyInventoryLotBalanceScope(wrapper, currentSnapshot());
        Page<InventoryLotBalanceEntity> result = inventoryLotBalanceMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toLotBalanceResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public InventoryLotBalanceResponse getLotBalanceById(Long id) {
        InventoryLotBalanceEntity entity = inventoryLotBalanceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("批次库存余额不存在");
        }
        CurrentUser user = currentUser();
        if (!Objects.equals(entity.getCompanyId(), user.companyId())
                || !Objects.equals(entity.getAccountBookId(), user.accountBookId())) {
            throw new IllegalArgumentException("批次库存余额不存在");
        }
        dataScopeService.assertCanViewInventoryLotBalance(entity, currentSnapshot());
        return toLotBalanceResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotTraceResponse> traceLot(InventoryLotTraceQuery query) {
        InventoryLotTraceQuery safeQuery = query == null ? new InventoryLotTraceQuery() : query;
        if (safeQuery.getProductId() == null) {
            throw new IllegalArgumentException("批次追溯必须指定商品");
        }
        String lotNo = normalizeNullableText(safeQuery.getLotNo());
        if (!StringUtils.hasText(lotNo)) {
            throw new IllegalArgumentException("批次追溯必须指定批次号");
        }
        CurrentUser user = currentUser();
        Page<InventoryTransactionEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = buildLotTraceQuery(
                user.companyId(),
                user.accountBookId(),
                safeQuery,
                lotNo
        );
        wrapper = dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
        Page<InventoryTransactionEntity> result = inventoryTransactionMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toLotTraceResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotExpiryAlertResponse> listLotExpiryAlerts(InventoryLotExpiryAlertQuery query) {
        InventoryLotExpiryAlertQuery safeQuery = query == null ? new InventoryLotExpiryAlertQuery() : query;
        CurrentUser user = currentUser();
        LocalDate referenceDate = LocalDate.now(clock);
        int warningDays = normalizeWarningDays(safeQuery.getWarningDays());
        String status = normalizeAlertStatus(safeQuery.getStatus());
        Page<InventoryLotBalanceEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = buildLotExpiryAlertQuery(
                user.companyId(),
                user.accountBookId(),
                safeQuery,
                referenceDate,
                warningDays,
                status
        );
        wrapper = dataScopeService.applyInventoryLotBalanceScope(wrapper, currentSnapshot());
        Page<InventoryLotBalanceEntity> result = inventoryLotBalanceMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toLotExpiryAlertResponse(entity, referenceDate))
                        .toList()
        );
    }

    private LambdaQueryWrapper<InventoryLotBalanceEntity> buildLotBalanceQuery(
            Long companyId,
            Long accountBookId,
            InventoryLotBalancePageQuery query
    ) {
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
                .eq(InventoryLotBalanceEntity::getAccountBookId, accountBookId);
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryLotBalanceEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryLotBalanceEntity::getProductId, query.getProductId());
        }
        String lotNo = normalizeNullableText(query.getLotNo());
        if (StringUtils.hasText(lotNo)) {
            wrapper.like(InventoryLotBalanceEntity::getLotNo, escapeLikeValue(lotNo));
        }
        if (query.getExpiryDateFrom() != null) {
            wrapper.ge(InventoryLotBalanceEntity::getExpiryDate, query.getExpiryDateFrom());
        }
        if (query.getExpiryDateTo() != null) {
            wrapper.le(InventoryLotBalanceEntity::getExpiryDate, query.getExpiryDateTo());
        }
        if (query.getExpiringWithinDays() != null) {
            int days = Math.max(query.getExpiringWithinDays(), 0);
            wrapper.le(InventoryLotBalanceEntity::getExpiryDate, LocalDate.now(clock).plusDays(days));
        }
        return wrapper
                .orderByAsc(InventoryLotBalanceEntity::getExpiryDate)
                .orderByAsc(InventoryLotBalanceEntity::getFirstInboundTime)
                .orderByAsc(InventoryLotBalanceEntity::getId);
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> buildLotTraceQuery(
            Long companyId,
            Long accountBookId,
            InventoryLotTraceQuery query,
            String lotNo
    ) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, companyId)
                .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                .eq(InventoryTransactionEntity::getProductId, query.getProductId())
                .eq(InventoryTransactionEntity::getLotNo, lotNo);
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryTransactionEntity::getWarehouseId, query.getWarehouseId());
        }
        String direction = normalizeUpper(query.getDirection());
        if (StringUtils.hasText(direction)) {
            wrapper.eq(InventoryTransactionEntity::getDirection, direction);
        }
        if (query.getOccurredTimeFrom() != null) {
            wrapper.ge(InventoryTransactionEntity::getOccurredTime, query.getOccurredTimeFrom());
        }
        if (query.getOccurredTimeTo() != null) {
            wrapper.le(InventoryTransactionEntity::getOccurredTime, query.getOccurredTimeTo());
        }
        return wrapper
                .orderByDesc(InventoryTransactionEntity::getOccurredTime)
                .orderByDesc(InventoryTransactionEntity::getId);
    }

    private LambdaQueryWrapper<InventoryLotBalanceEntity> buildLotExpiryAlertQuery(
            Long companyId,
            Long accountBookId,
            InventoryLotExpiryAlertQuery query,
            LocalDate referenceDate,
            int warningDays,
            String status
    ) {
        LocalDate warningDate = referenceDate.plusDays(warningDays);
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
                .eq(InventoryLotBalanceEntity::getAccountBookId, accountBookId)
                .isNotNull(InventoryLotBalanceEntity::getExpiryDate)
                .apply("qty_on_hand - qty_reserved > 0");
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryLotBalanceEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryLotBalanceEntity::getProductId, query.getProductId());
        }
        String lotNo = normalizeNullableText(query.getLotNo());
        if (StringUtils.hasText(lotNo)) {
            wrapper.like(InventoryLotBalanceEntity::getLotNo, escapeLikeValue(lotNo));
        }
        if ("EXPIRED".equals(status)) {
            wrapper.lt(InventoryLotBalanceEntity::getExpiryDate, referenceDate);
        } else if ("EXPIRING".equals(status)) {
            wrapper.ge(InventoryLotBalanceEntity::getExpiryDate, referenceDate)
                    .le(InventoryLotBalanceEntity::getExpiryDate, warningDate);
        } else {
            wrapper.le(InventoryLotBalanceEntity::getExpiryDate, warningDate);
        }
        return wrapper
                .orderByAsc(InventoryLotBalanceEntity::getExpiryDate)
                .orderByAsc(InventoryLotBalanceEntity::getFirstInboundTime)
                .orderByAsc(InventoryLotBalanceEntity::getId);
    }

    private InventoryLotBalanceResponse toLotBalanceResponse(InventoryLotBalanceEntity entity) {
        return new InventoryLotBalanceResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getLocationId(),
                entity.getProductId(),
                entity.getLotNo(),
                entity.getProductionDate(),
                entity.getExpiryDate(),
                entity.getFirstInboundTime(),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyOnHand())),
                qtyReserved(entity),
                qtyAvailable(entity),
                entity.getAmountOnHand(),
                entity.getUpdatedTime()
        );
    }

    private InventoryLotTraceResponse toLotTraceResponse(InventoryTransactionEntity entity) {
        String bizType = entity.getBizType();
        return new InventoryLotTraceResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getLocationId(),
                entity.getProductId(),
                entity.getLotNo(),
                entity.getProductionDate(),
                entity.getExpiryDate(),
                bizType,
                entity.getBizNo(),
                entity.getBizLineId(),
                entity.getDirection(),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQty())),
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmount())),
                ScalePrecision.unitCost(
                        ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmount())),
                        ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQty()))
                ),
                entity.getOccurredTime(),
                entity.getRemark(),
                documentLinkResolver.resolveRoute(bizType, entity.getBizNo()),
                documentLinkResolver.resolveLabel(bizType)
        );
    }

    private InventoryLotExpiryAlertResponse toLotExpiryAlertResponse(
            InventoryLotBalanceEntity entity,
            LocalDate referenceDate
    ) {
        java.math.BigDecimal qtyOnHand = ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyOnHand()));
        java.math.BigDecimal reserved = qtyReserved(entity);
        java.math.BigDecimal available = ScalePrecision.quantity(qtyOnHand.subtract(reserved));
        return new InventoryLotExpiryAlertResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getProductId(),
                entity.getLotNo(),
                entity.getProductionDate(),
                entity.getExpiryDate(),
                entity.getFirstInboundTime(),
                qtyOnHand,
                reserved,
                available,
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmountOnHand())),
                expiryStatus(entity.getExpiryDate(), referenceDate),
                ChronoUnit.DAYS.between(referenceDate, entity.getExpiryDate()),
                entity.getUpdatedTime()
        );
    }

    private java.math.BigDecimal qtyReserved(InventoryLotBalanceEntity entity) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyReserved()));
    }

    private java.math.BigDecimal qtyAvailable(InventoryLotBalanceEntity entity) {
        return ScalePrecision.quantity(
                ScalePrecision.zeroDefault(entity.getQtyOnHand()).subtract(qtyReserved(entity))
        );
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
    }

    private CurrentUser currentUser() {
        return currentUserContext.requireCurrentUser();
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private int normalizeWarningDays(Integer warningDays) {
        if (warningDays == null) {
            return DEFAULT_EXPIRY_WARNING_DAYS;
        }
        return Math.min(Math.max(warningDays, 0), MAX_EXPIRY_WARNING_DAYS);
    }

    private String normalizeAlertStatus(String status) {
        String normalized = normalizeUpper(status);
        if (normalized == null) {
            return null;
        }
        if (!"EXPIRED".equals(normalized) && !"EXPIRING".equals(normalized)) {
            throw new IllegalArgumentException("预警状态不正确");
        }
        return normalized;
    }

    private String expiryStatus(LocalDate expiryDate, LocalDate referenceDate) {
        return expiryDate.isBefore(referenceDate) ? "EXPIRED" : "EXPIRING";
    }

    private String escapeLikeValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
