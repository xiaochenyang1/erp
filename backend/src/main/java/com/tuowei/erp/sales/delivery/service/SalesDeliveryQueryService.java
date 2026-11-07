package com.tuowei.erp.sales.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLineResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Read-side filtering, data scope and response mapping for sales deliveries. */
@Service
public class SalesDeliveryQueryService {

    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public SalesDeliveryQueryService(
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public SalesDeliveryResponse getById(Long id) {
        SalesDeliveryEntity delivery = requireDelivery(id);
        assertCanView(delivery);
        List<SalesDeliveryLineEntity> lines = salesDeliveryLineMapper.selectList(
                new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId())
                        .orderByAsc(SalesDeliveryLineEntity::getLineNo)
        );
        return toResponse(delivery, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDeliveryResponse> list(SalesDeliveryPageQuery query) {
        SalesDeliveryPageQuery safeQuery = query == null ? new SalesDeliveryPageQuery() : query;
        Page<SalesDeliveryEntity> result = salesDeliveryMapper.selectPage(
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
    public void assertCanView(SalesDeliveryEntity delivery) {
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

    @Transactional(readOnly = true)
    public void assertCanView(SalesOrderEntity order) {
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

    public SalesDeliveryResponse toResponse(
            SalesDeliveryEntity delivery,
            List<SalesDeliveryLineEntity> lines
    ) {
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
                delivery.getLogisticsStatus(),
                delivery.getDeliveredBy(),
                delivery.getDeliveredTime(),
                delivery.getDeliveryProofAttachmentId(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private LambdaQueryWrapper<SalesDeliveryEntity> scopedListQuery(SalesDeliveryPageQuery query) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<SalesDeliveryEntity> wrapper = buildListQuery(
                normalizeNullableText(query.getKeyword()),
                query.getOrderId(),
                query.getWarehouseId(),
                normalizeStatus(query.getStatus()),
                normalizeStatus(query.getLogisticsStatus()),
                normalizeNullableText(query.getTrackingNo()),
                query.getDeliveryDateFrom(),
                query.getDeliveryDateTo()
        );
        return dataScopeService.applySalesDeliveryScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private LambdaQueryWrapper<SalesDeliveryEntity> buildListQuery(
            String keyword,
            Long orderId,
            Long warehouseId,
            String status,
            String logisticsStatus,
            String trackingNo,
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
        if (StringUtils.hasText(logisticsStatus)) {
            wrapper.eq(SalesDeliveryEntity::getLogisticsStatus, logisticsStatus);
        }
        if (StringUtils.hasText(trackingNo)) {
            wrapper.like(SalesDeliveryEntity::getTrackingNo, trackingNo);
        }
        if (deliveryDateFrom != null) {
            wrapper.ge(SalesDeliveryEntity::getDeliveryDate, deliveryDateFrom);
        }
        if (deliveryDateTo != null) {
            wrapper.le(SalesDeliveryEntity::getDeliveryDate, deliveryDateTo);
        }
        return wrapper.orderByDesc(SalesDeliveryEntity::getId);
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
                delivery.getLogisticsStatus(),
                delivery.getDeliveredBy(),
                delivery.getDeliveredTime(),
                delivery.getDeliveryProofAttachmentId(),
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
                line.getLocationId(),
                line.getSerialNos(),
                line.getRemark()
        );
    }

    private SalesDeliveryEntity requireDelivery(Long id) {
        SalesDeliveryEntity delivery = salesDeliveryMapper.selectById(id);
        if (delivery == null || delivery.getDeletedFlag() == null || delivery.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售出库单不存在");
        }
        return delivery;
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
