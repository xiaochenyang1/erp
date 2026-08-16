package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportResponse;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Inventory report filtering, tenant/data scope, DTO mapping and streaming export. */
@Service
public class InventoryReportQueryService {

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ReportProperties reportProperties;

    public InventoryReportQueryService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ReportProperties reportProperties
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceReportResponse> listInventoryBalances(InventoryBalanceReportQuery query) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        Page<InventoryBalanceEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<InventoryBalanceEntity> result = inventoryBalanceMapper.selectPage(
                page,
                inventoryBalanceWrapper(safeQuery, true)
        );
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toInventoryBalanceReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertInventoryBalanceExportWithinLimit(InventoryBalanceReportQuery query) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        assertExportRowsWithinLimit(countExportRows(inventoryBalanceMapper, inventoryBalanceWrapper(safeQuery, false)));
    }

    @Transactional(readOnly = true)
    public void streamInventoryBalances(
            InventoryBalanceReportQuery query,
            Consumer<InventoryBalanceReportResponse> consumer
    ) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        streamExportRows(
                inventoryBalanceMapper,
                inventoryBalanceWrapper(safeQuery, true),
                this::toInventoryBalanceReport,
                consumer
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionReportResponse> listInventoryTransactions(
            InventoryTransactionReportQuery query
    ) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        Page<InventoryTransactionEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<InventoryTransactionEntity> result = inventoryTransactionMapper.selectPage(
                page,
                inventoryTransactionWrapper(safeQuery, true)
        );
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toInventoryTransactionReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertInventoryTransactionExportWithinLimit(InventoryTransactionReportQuery query) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        assertExportRowsWithinLimit(countExportRows(
                inventoryTransactionMapper,
                inventoryTransactionWrapper(safeQuery, false)
        ));
    }

    @Transactional(readOnly = true)
    public void streamInventoryTransactions(
            InventoryTransactionReportQuery query,
            Consumer<InventoryTransactionReportResponse> consumer
    ) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        streamExportRows(
                inventoryTransactionMapper,
                inventoryTransactionWrapper(safeQuery, true),
                this::toInventoryTransactionReport,
                consumer
        );
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> inventoryBalanceWrapper(
            InventoryBalanceReportQuery query,
            boolean ordered
    ) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, currentUser.accountBookId());
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryBalanceEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryBalanceEntity::getProductId, query.getProductId());
        }
        if (ordered) {
            wrapper.orderByAsc(InventoryBalanceEntity::getWarehouseId)
                    .orderByAsc(InventoryBalanceEntity::getProductId)
                    .orderByDesc(InventoryBalanceEntity::getId);
        }
        return dataScopeService.applyInventoryBalanceScope(wrapper, currentSnapshot());
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionWrapper(
            InventoryTransactionReportQuery query,
            boolean ordered
    ) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, currentUser.accountBookId());
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryTransactionEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryTransactionEntity::getProductId, query.getProductId());
        }
        String bizType = normalizeUpper(query.getBizType());
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(InventoryTransactionEntity::getBizType, bizType);
        }
        String bizNo = normalizeNullableText(query.getBizNo());
        if (StringUtils.hasText(bizNo)) {
            wrapper.like(InventoryTransactionEntity::getBizNo, bizNo);
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
        if (ordered) {
            wrapper.orderByDesc(InventoryTransactionEntity::getOccurredTime)
                    .orderByDesc(InventoryTransactionEntity::getId);
        }
        return dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
    }

    private InventoryBalanceReportResponse toInventoryBalanceReport(InventoryBalanceEntity entity) {
        return new InventoryBalanceReportResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getProductId(),
                entity.getQtyOnHand(),
                qtyReserved(entity),
                qtyAvailable(entity),
                entity.getAmountOnHand(),
                entity.getUpdatedTime()
        );
    }

    private BigDecimal qtyReserved(InventoryBalanceEntity entity) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyReserved()));
    }

    private BigDecimal qtyAvailable(InventoryBalanceEntity entity) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyOnHand()).subtract(qtyReserved(entity)));
    }

    private InventoryTransactionReportResponse toInventoryTransactionReport(InventoryTransactionEntity entity) {
        return new InventoryTransactionReportResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getProductId(),
                entity.getBizType(),
                entity.getBizNo(),
                entity.getBizLineId(),
                entity.getDirection(),
                entity.getQty(),
                entity.getAmount(),
                entity.getUnitCost(),
                entity.getOccurredTime(),
                entity.getRemark()
        );
    }

    private <T> long countExportRows(BaseMapper<T> mapper, LambdaQueryWrapper<T> wrapper) {
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private <T, R> void streamExportRows(
            BaseMapper<T> mapper,
            LambdaQueryWrapper<T> wrapper,
            Function<T, R> responseMapper,
            Consumer<R> consumer
    ) {
        long pageNo = 1;
        long batchSize = exportBatchSize();
        while (true) {
            Page<T> page = new Page<>(pageNo, batchSize, false);
            page.setMaxLimit(batchSize);
            List<T> records = mapper.selectPage(page, wrapper).getRecords();
            if (records.isEmpty()) {
                return;
            }
            records.stream().map(responseMapper).forEach(consumer);
            if (records.size() < batchSize) {
                return;
            }
            pageNo++;
        }
    }

    private long exportBatchSize() {
        return Math.min(reportProperties.exportBatchSize(), reportProperties.maxExportRows());
    }

    private void assertExportRowsWithinLimit(long rowCount) {
        int maxRows = reportProperties.maxExportRows();
        if (rowCount > maxRows) {
            throw new IllegalArgumentException("导出结果超过" + maxRows + "行，请缩小筛选范围后重试");
        }
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
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
