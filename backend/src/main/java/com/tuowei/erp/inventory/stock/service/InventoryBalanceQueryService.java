package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.web.InventoryBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryBalanceResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Read-side query, tenant guarding, DTO mapping and export batching for inventory balances. */
@Service
public class InventoryBalanceQueryService {

    private static final int EXPORT_PAGE_SIZE = 5000;
    private static final List<String> BALANCE_EXPORT_HEADERS = List.of(
            "warehouseId",
            "productId",
            "qtyOnHand",
            "qtyReserved",
            "qtyAvailable",
            "amountOnHand",
            "updatedTime"
    );

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;

    public InventoryBalanceQueryService(
            InventoryBalanceMapper inventoryBalanceMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceResponse> listBalances(InventoryBalancePageQuery query) {
        InventoryBalancePageQuery safeQuery = query == null ? new InventoryBalancePageQuery() : query;
        CurrentUser user = currentUser();
        Page<InventoryBalanceEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = buildBalanceQuery(
                user.companyId(),
                user.accountBookId(),
                safeQuery
        );
        wrapper = dataScopeService.applyInventoryBalanceScope(wrapper, currentSnapshot());
        Page<InventoryBalanceEntity> result = inventoryBalanceMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toBalanceResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public StreamingResponseBody exportBalances(InventoryBalancePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        InventoryBalancePageQuery safeQuery = exportBalanceQuery(query);
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(
                outputStream,
                BALANCE_EXPORT_HEADERS,
                rowWriter -> {
                    CurrentUser user = currentUser();
                    Page<InventoryBalanceEntity> page = new Page<>(1L, EXPORT_PAGE_SIZE);
                    LambdaQueryWrapper<InventoryBalanceEntity> wrapper = buildBalanceQuery(
                            user.companyId(),
                            user.accountBookId(),
                            safeQuery
                    );
                    wrapper = dataScopeService.applyInventoryBalanceScope(wrapper, currentSnapshot());
                    Page<InventoryBalanceEntity> result = inventoryBalanceMapper.selectPage(page, wrapper);
                    for (InventoryBalanceEntity entity : result.getRecords()) {
                        rowWriter.write(balanceExportRow(toBalanceResponse(entity)));
                    }
                }
        ));
    }

    @Transactional(readOnly = true)
    public InventoryBalanceResponse getBalanceById(Long id) {
        InventoryBalanceEntity entity = inventoryBalanceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("库存余额不存在");
        }
        CurrentUser user = currentUser();
        if (!Objects.equals(entity.getCompanyId(), user.companyId())
                || !Objects.equals(entity.getAccountBookId(), user.accountBookId())) {
            throw new IllegalArgumentException("库存余额不存在");
        }
        dataScopeService.assertCanViewInventoryBalance(entity, currentSnapshot());
        return toBalanceResponse(entity);
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> buildBalanceQuery(
            Long companyId,
            Long accountBookId,
            InventoryBalancePageQuery query
    ) {
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId);
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryBalanceEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(InventoryBalanceEntity::getProductId, query.getProductId());
        }
        if (query.getLocationId() != null) {
            wrapper.eq(InventoryBalanceEntity::getLocationId, query.getLocationId());
        }
        return wrapper
                .orderByAsc(InventoryBalanceEntity::getWarehouseId)
                .orderByAsc(InventoryBalanceEntity::getLocationId)
                .orderByAsc(InventoryBalanceEntity::getProductId)
                .orderByDesc(InventoryBalanceEntity::getId);
    }

    private InventoryBalancePageQuery exportBalanceQuery(InventoryBalancePageQuery query) {
        InventoryBalancePageQuery source = query == null ? new InventoryBalancePageQuery() : query;
        InventoryBalancePageQuery safeQuery = new InventoryBalancePageQuery();
        safeQuery.setPageNo(1);
        safeQuery.setPageSize(EXPORT_PAGE_SIZE);
        safeQuery.setWarehouseId(source.getWarehouseId());
        safeQuery.setProductId(source.getProductId());
        safeQuery.setLocationId(source.getLocationId());
        return safeQuery;
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
    }

    private CurrentUser currentUser() {
        return currentUserContext.requireCurrentUser();
    }

    private InventoryBalanceResponse toBalanceResponse(InventoryBalanceEntity entity) {
        return new InventoryBalanceResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getLocationId(),
                entity.getProductId(),
                entity.getQtyOnHand(),
                qtyReserved(entity),
                qtyAvailable(entity),
                entity.getAmountOnHand(),
                entity.getUpdatedTime()
        );
    }

    private List<?> balanceExportRow(InventoryBalanceResponse record) {
        return Arrays.asList(
                record.warehouseId(),
                record.productId(),
                record.qtyOnHand(),
                record.qtyReserved(),
                record.qtyAvailable(),
                record.amountOnHand(),
                record.updatedTime()
        );
    }

    private java.math.BigDecimal qtyReserved(InventoryBalanceEntity entity) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyReserved()));
    }

    private java.math.BigDecimal qtyAvailable(InventoryBalanceEntity entity) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyOnHand()).subtract(qtyReserved(entity)));
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

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}
