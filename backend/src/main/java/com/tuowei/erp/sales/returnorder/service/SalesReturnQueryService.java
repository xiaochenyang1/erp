package com.tuowei.erp.sales.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Read-side filtering, data scope and response mapping for sales returns. */
@Service
public class SalesReturnQueryService {

    private final SalesReturnMapper salesReturnMapper;
    private final SalesReturnLineMapper salesReturnLineMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public SalesReturnQueryService(
            SalesReturnMapper salesReturnMapper,
            SalesReturnLineMapper salesReturnLineMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.salesReturnMapper = salesReturnMapper;
        this.salesReturnLineMapper = salesReturnLineMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesReturnResponse> list(SalesReturnPageQuery query) {
        SalesReturnPageQuery safeQuery = query == null ? new SalesReturnPageQuery() : query;
        Page<SalesReturnEntity> result = salesReturnMapper.selectPage(
                new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize())),
                scopedListQuery(safeQuery)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse getById(Long id) {
        SalesReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        List<SalesReturnLineEntity> lines = salesReturnLineMapper.selectList(
                new LambdaQueryWrapper<SalesReturnLineEntity>()
                        .eq(SalesReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(SalesReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(SalesReturnLineEntity::getReturnId, entity.getId())
                        .orderByAsc(SalesReturnLineEntity::getLineNo)
        );
        return toResponse(entity, lines);
    }

    @Transactional(readOnly = true)
    public void assertCanView(SalesReturnEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesReturn(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(SalesDeliveryEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesDelivery(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    @Transactional(readOnly = true)
    public void assertCanView(SalesOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    public SalesReturnResponse toResponse(
            SalesReturnEntity entity,
            List<SalesReturnLineEntity> lines
    ) {
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getDeliveryId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private LambdaQueryWrapper<SalesReturnEntity> scopedListQuery(SalesReturnPageQuery query) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<SalesReturnEntity> wrapper = buildListQuery(
                normalizeNullableText(query.getKeyword()),
                query.getDeliveryId(),
                query.getWarehouseId(),
                normalizeStatus(query.getStatus()),
                query.getReturnDateFrom(),
                query.getReturnDateTo()
        );
        return dataScopeService.applySalesReturnScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private LambdaQueryWrapper<SalesReturnEntity> buildListQuery(
            String keyword,
            Long deliveryId,
            Long warehouseId,
            String status,
            LocalDate returnDateFrom,
            LocalDate returnDateTo
    ) {
        LambdaQueryWrapper<SalesReturnEntity> wrapper = new LambdaQueryWrapper<SalesReturnEntity>()
                .eq(SalesReturnEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SalesReturnEntity::getReturnNo, keyword);
        }
        if (deliveryId != null) {
            wrapper.eq(SalesReturnEntity::getDeliveryId, deliveryId);
        }
        if (warehouseId != null) {
            wrapper.eq(SalesReturnEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesReturnEntity::getStatus, status);
        }
        if (returnDateFrom != null) {
            wrapper.ge(SalesReturnEntity::getReturnDate, returnDateFrom);
        }
        if (returnDateTo != null) {
            wrapper.le(SalesReturnEntity::getReturnDate, returnDateTo);
        }
        return wrapper.orderByDesc(SalesReturnEntity::getId);
    }

    private SalesReturnResponse toSummaryResponse(SalesReturnEntity entity) {
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getDeliveryId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private SalesReturnLineResponse toLineResponse(SalesReturnLineEntity line) {
        return new SalesReturnLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getDeliveryLineId(),
                line.getOrderLineId(),
                line.getProductId(),
                line.getQty(),
                line.getPrice(),
                line.getTaxRate(),
                line.getAmount(),
                line.getTaxAmount(),
                line.getLotNo(),
                line.getProductionDate(),
                line.getExpiryDate(),
                line.getLocationId(),
                line.getSerialNos(),
                line.getRemark()
        );
    }

    private SalesReturnEntity requireReturn(Long id) {
        SalesReturnEntity entity = salesReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售退货单不存在");
        }
        return entity;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
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
