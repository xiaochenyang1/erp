package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/** Read-side query, tenant guarding and DTO mapping for inventory transactions. */
@Service
public class InventoryTransactionQueryService {

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;

    public InventoryTransactionQueryService(
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> listTransactions(InventoryTransactionPageQuery query) {
        InventoryTransactionPageQuery safeQuery = query == null ? new InventoryTransactionPageQuery() : query;
        CurrentUser user = currentUserContext.requireCurrentUser();
        Page<InventoryTransactionEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = buildTransactionQuery(
                user.companyId(),
                user.accountBookId(),
                safeQuery
        );
        wrapper = dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
        Page<InventoryTransactionEntity> result = inventoryTransactionMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toTransactionResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public InventoryTransactionResponse getTransactionById(Long id) {
        InventoryTransactionEntity entity = inventoryTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("库存流水不存在");
        }
        CurrentUser user = currentUserContext.requireCurrentUser();
        if (!Objects.equals(entity.getCompanyId(), user.companyId())
                || !Objects.equals(entity.getAccountBookId(), user.accountBookId())) {
            throw new IllegalArgumentException("库存流水不存在");
        }
        dataScopeService.assertCanViewInventoryTransaction(entity, currentSnapshot());
        return toTransactionResponse(entity);
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> buildTransactionQuery(
            Long companyId,
            Long accountBookId,
            InventoryTransactionPageQuery query
    ) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, companyId)
                .eq(InventoryTransactionEntity::getAccountBookId, accountBookId);
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
        return wrapper
                .orderByDesc(InventoryTransactionEntity::getOccurredTime)
                .orderByDesc(InventoryTransactionEntity::getId);
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
    }

    private InventoryTransactionResponse toTransactionResponse(InventoryTransactionEntity entity) {
        return new InventoryTransactionResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getLocationId(),
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
}
