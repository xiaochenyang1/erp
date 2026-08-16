package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Purchase and sales order report filtering, data scope, mapping and streaming export. */
@Service
public class OrderReportQueryService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final ReportProperties reportProperties;

    public OrderReportQueryService(
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            ReportProperties reportProperties
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listPurchaseOrders(PurchaseOrderReportQuery query) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        Page<PurchaseOrderEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<PurchaseOrderEntity> result = purchaseOrderMapper.selectPage(
                page,
                purchaseOrderWrapper(safeQuery, true)
        );
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toPurchaseOrderReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertPurchaseOrderExportWithinLimit(PurchaseOrderReportQuery query) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        assertExportRowsWithinLimit(countExportRows(purchaseOrderMapper, purchaseOrderWrapper(safeQuery, false)));
    }

    @Transactional(readOnly = true)
    public void streamPurchaseOrders(PurchaseOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        streamExportRows(
                purchaseOrderMapper,
                purchaseOrderWrapper(safeQuery, true),
                this::toPurchaseOrderReport,
                consumer
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listSalesOrders(SalesOrderReportQuery query) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        Page<SalesOrderEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<SalesOrderEntity> result = salesOrderMapper.selectPage(page, salesOrderWrapper(safeQuery, true));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSalesOrderReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertSalesOrderExportWithinLimit(SalesOrderReportQuery query) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        assertExportRowsWithinLimit(countExportRows(salesOrderMapper, salesOrderWrapper(safeQuery, false)));
    }

    @Transactional(readOnly = true)
    public void streamSalesOrders(SalesOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        streamExportRows(
                salesOrderMapper,
                salesOrderWrapper(safeQuery, true),
                this::toSalesOrderReport,
                consumer
        );
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderWrapper(
            PurchaseOrderReportQuery query,
            boolean ordered
    ) {
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDeletedFlag, 0);
        if (query.getSupplierId() != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, query.getSupplierId());
        }
        if (query.getOrderDateFrom() != null) {
            wrapper.ge(PurchaseOrderEntity::getOrderDate, query.getOrderDateFrom());
        }
        if (query.getOrderDateTo() != null) {
            wrapper.le(PurchaseOrderEntity::getOrderDate, query.getOrderDateTo());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(query.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(PurchaseOrderEntity::getApprovalStatus, approvalStatus);
        }
        String keyword = normalizeNullableText(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseOrderEntity::getOrderNo, keyword);
        }

        ScopedUsers scopedUsers = scopedUsers();
        wrapper = dataScopeService.applyPurchaseOrderScope(
                wrapper,
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        );
        if (ordered) {
            wrapper.orderByDesc(PurchaseOrderEntity::getOrderDate).orderByDesc(PurchaseOrderEntity::getId);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SalesOrderEntity> salesOrderWrapper(SalesOrderReportQuery query, boolean ordered) {
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDeletedFlag, 0);
        if (query.getCustomerId() != null) {
            wrapper.eq(SalesOrderEntity::getCustomerId, query.getCustomerId());
        }
        if (query.getOrderDateFrom() != null) {
            wrapper.ge(SalesOrderEntity::getOrderDate, query.getOrderDateFrom());
        }
        if (query.getOrderDateTo() != null) {
            wrapper.le(SalesOrderEntity::getOrderDate, query.getOrderDateTo());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(query.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(SalesOrderEntity::getApprovalStatus, approvalStatus);
        }
        String deliveryStatus = normalizeUpper(query.getDeliveryStatus());
        if (StringUtils.hasText(deliveryStatus)) {
            wrapper.eq(SalesOrderEntity::getDeliveryStatus, deliveryStatus);
        }
        String keyword = normalizeNullableText(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SalesOrderEntity::getOrderNo, keyword);
        }

        ScopedUsers scopedUsers = scopedUsers();
        wrapper = dataScopeService.applySalesOrderScope(
                wrapper,
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        );
        if (ordered) {
            wrapper.orderByDesc(SalesOrderEntity::getOrderDate).orderByDesc(SalesOrderEntity::getId);
        }
        return wrapper;
    }

    private ScopedUsers scopedUsers() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        return new ScopedUsers(currentUser, snapshot, scopedUserIds.deptUserIds(), scopedUserIds.postUserIds());
    }

    private OrderReportResponse toPurchaseOrderReport(PurchaseOrderEntity entity) {
        return new OrderReportResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getSupplierId(),
                entity.getOrderDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getReceiptStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount()
        );
    }

    private OrderReportResponse toSalesOrderReport(SalesOrderEntity entity) {
        return new OrderReportResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getCustomerId(),
                entity.getOrderDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getDeliveryStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount()
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

    private record ScopedUsers(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
    }
}
