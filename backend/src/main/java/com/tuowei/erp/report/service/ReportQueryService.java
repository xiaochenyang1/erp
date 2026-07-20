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
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportResponse;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportResponse;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class ReportQueryService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final FinanceSettlementReportMapper financeSettlementReportMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;
    private final ReportProperties reportProperties;

    public ReportQueryService(
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            FinanceSettlementReportMapper financeSettlementReportMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            FinanceSettlementScopeSupport financeSettlementScopeSupport,
            ReportProperties reportProperties
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.financeSettlementReportMapper = financeSettlementReportMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listPurchaseOrders(PurchaseOrderReportQuery query) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        Page<PurchaseOrderEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDeletedFlag, 0);
        if (safeQuery.getSupplierId() != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, safeQuery.getSupplierId());
        }
        if (safeQuery.getOrderDateFrom() != null) {
            wrapper.ge(PurchaseOrderEntity::getOrderDate, safeQuery.getOrderDateFrom());
        }
        if (safeQuery.getOrderDateTo() != null) {
            wrapper.le(PurchaseOrderEntity::getOrderDate, safeQuery.getOrderDateTo());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(safeQuery.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(PurchaseOrderEntity::getApprovalStatus, approvalStatus);
        }
        String keyword = normalizeNullableText(safeQuery.getKeyword());
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
        wrapper.orderByDesc(PurchaseOrderEntity::getOrderDate).orderByDesc(PurchaseOrderEntity::getId);
        Page<PurchaseOrderEntity> result = purchaseOrderMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toPurchaseOrderReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertPurchaseOrderExportWithinLimit(PurchaseOrderReportQuery query) {
        assertExportRowsWithinLimit(countExportRows(purchaseOrderMapper, purchaseOrderExportWrapper(query, false)));
    }

    @Transactional(readOnly = true)
    public void streamPurchaseOrders(PurchaseOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        streamExportRows(purchaseOrderMapper, purchaseOrderExportWrapper(query, true), this::toPurchaseOrderReport, consumer);
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderExportWrapper(PurchaseOrderReportQuery query, boolean ordered) {
        PurchaseOrderReportQuery safeQuery = query == null ? new PurchaseOrderReportQuery() : query;
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDeletedFlag, 0);
        if (safeQuery.getSupplierId() != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, safeQuery.getSupplierId());
        }
        if (safeQuery.getOrderDateFrom() != null) {
            wrapper.ge(PurchaseOrderEntity::getOrderDate, safeQuery.getOrderDateFrom());
        }
        if (safeQuery.getOrderDateTo() != null) {
            wrapper.le(PurchaseOrderEntity::getOrderDate, safeQuery.getOrderDateTo());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(safeQuery.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(PurchaseOrderEntity::getApprovalStatus, approvalStatus);
        }
        String keyword = normalizeNullableText(safeQuery.getKeyword());
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

    @Transactional(readOnly = true)
    public PageResponse<OrderReportResponse> listSalesOrders(SalesOrderReportQuery query) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        Page<SalesOrderEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDeletedFlag, 0);
        if (safeQuery.getCustomerId() != null) {
            wrapper.eq(SalesOrderEntity::getCustomerId, safeQuery.getCustomerId());
        }
        if (safeQuery.getOrderDateFrom() != null) {
            wrapper.ge(SalesOrderEntity::getOrderDate, safeQuery.getOrderDateFrom());
        }
        if (safeQuery.getOrderDateTo() != null) {
            wrapper.le(SalesOrderEntity::getOrderDate, safeQuery.getOrderDateTo());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(safeQuery.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(SalesOrderEntity::getApprovalStatus, approvalStatus);
        }
        String deliveryStatus = normalizeUpper(safeQuery.getDeliveryStatus());
        if (StringUtils.hasText(deliveryStatus)) {
            wrapper.eq(SalesOrderEntity::getDeliveryStatus, deliveryStatus);
        }
        String keyword = normalizeNullableText(safeQuery.getKeyword());
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
        wrapper.orderByDesc(SalesOrderEntity::getOrderDate).orderByDesc(SalesOrderEntity::getId);
        Page<SalesOrderEntity> result = salesOrderMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSalesOrderReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertSalesOrderExportWithinLimit(SalesOrderReportQuery query) {
        assertExportRowsWithinLimit(countExportRows(salesOrderMapper, salesOrderExportWrapper(query, false)));
    }

    @Transactional(readOnly = true)
    public void streamSalesOrders(SalesOrderReportQuery query, Consumer<OrderReportResponse> consumer) {
        streamExportRows(salesOrderMapper, salesOrderExportWrapper(query, true), this::toSalesOrderReport, consumer);
    }

    private LambdaQueryWrapper<SalesOrderEntity> salesOrderExportWrapper(SalesOrderReportQuery query, boolean ordered) {
        SalesOrderReportQuery safeQuery = query == null ? new SalesOrderReportQuery() : query;
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDeletedFlag, 0);
        if (safeQuery.getCustomerId() != null) {
            wrapper.eq(SalesOrderEntity::getCustomerId, safeQuery.getCustomerId());
        }
        if (safeQuery.getOrderDateFrom() != null) {
            wrapper.ge(SalesOrderEntity::getOrderDate, safeQuery.getOrderDateFrom());
        }
        if (safeQuery.getOrderDateTo() != null) {
            wrapper.le(SalesOrderEntity::getOrderDate, safeQuery.getOrderDateTo());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesOrderEntity::getStatus, status);
        }
        String approvalStatus = normalizeUpper(safeQuery.getApprovalStatus());
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(SalesOrderEntity::getApprovalStatus, approvalStatus);
        }
        String deliveryStatus = normalizeUpper(safeQuery.getDeliveryStatus());
        if (StringUtils.hasText(deliveryStatus)) {
            wrapper.eq(SalesOrderEntity::getDeliveryStatus, deliveryStatus);
        }
        String keyword = normalizeNullableText(safeQuery.getKeyword());
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

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceReportResponse> listInventoryBalances(InventoryBalanceReportQuery query) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        Page<InventoryBalanceEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = inventoryBalanceWrapper(safeQuery);
        wrapper = dataScopeService.applyInventoryBalanceScope(wrapper, currentSnapshot());
        Page<InventoryBalanceEntity> result = inventoryBalanceMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toInventoryBalanceReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertInventoryBalanceExportWithinLimit(InventoryBalanceReportQuery query) {
        assertExportRowsWithinLimit(countExportRows(inventoryBalanceMapper, inventoryBalanceExportWrapper(query, false)));
    }

    @Transactional(readOnly = true)
    public void streamInventoryBalances(InventoryBalanceReportQuery query, Consumer<InventoryBalanceReportResponse> consumer) {
        streamExportRows(inventoryBalanceMapper, inventoryBalanceExportWrapper(query, true), this::toInventoryBalanceReport, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionReportResponse> listInventoryTransactions(InventoryTransactionReportQuery query) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        Page<InventoryTransactionEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = inventoryTransactionWrapper(safeQuery);
        wrapper = dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
        Page<InventoryTransactionEntity> result = inventoryTransactionMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toInventoryTransactionReport).toList()
        );
    }

    @Transactional(readOnly = true)
    public void assertInventoryTransactionExportWithinLimit(InventoryTransactionReportQuery query) {
        assertExportRowsWithinLimit(countExportRows(inventoryTransactionMapper, inventoryTransactionExportWrapper(query, false)));
    }

    @Transactional(readOnly = true)
    public void streamInventoryTransactions(InventoryTransactionReportQuery query, Consumer<InventoryTransactionReportResponse> consumer) {
        streamExportRows(inventoryTransactionMapper, inventoryTransactionExportWrapper(query, true), this::toInventoryTransactionReport, consumer);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceSettlementReportResponse> listFinanceSettlements(FinanceSettlementReportQuery query) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            return listPayables(safeQuery);
        }
        if ("RECEIVABLE".equals(direction)) {
            return listReceivables(safeQuery);
        }
        return listAllSettlements(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertFinanceSettlementExportWithinLimit(FinanceSettlementReportQuery query) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            assertExportRowsWithinLimit(countExportRows(payableMapper, payableWrapper(safeQuery)));
            return;
        }
        if ("RECEIVABLE".equals(direction)) {
            assertExportRowsWithinLimit(countExportRows(receivableMapper, receivableWrapper(safeQuery)));
            return;
        }
        long payableCount = countExportRows(payableMapper, payableWrapper(safeQuery));
        long receivableCount = countExportRows(receivableMapper, receivableWrapper(safeQuery));
        assertExportRowsWithinLimit(payableCount + receivableCount);
    }

    @Transactional(readOnly = true)
    public void streamFinanceSettlements(FinanceSettlementReportQuery query, Consumer<FinanceSettlementReportResponse> consumer) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            streamExportRows(
                    payableMapper,
                    payableWrapper(safeQuery).orderByDesc(PayableEntity::getBizDate).orderByDesc(PayableEntity::getId),
                    this::toPayableReport,
                    consumer
            );
            return;
        }
        if ("RECEIVABLE".equals(direction)) {
            streamExportRows(
                    receivableMapper,
                    receivableWrapper(safeQuery).orderByDesc(ReceivableEntity::getBizDate).orderByDesc(ReceivableEntity::getId),
                    this::toReceivableReport,
                    consumer
            );
            return;
        }
        streamAllFinanceSettlements(safeQuery, consumer);
    }

    private PageResponse<FinanceSettlementReportResponse> listPayables(FinanceSettlementReportQuery query) {
        Page<PayableEntity> page = new Page<>(normalizePageNo(query.getPageNo()), normalizePageSize(query.getPageSize()));
        Page<PayableEntity> result = payableMapper.selectPage(page, payableWrapper(query).orderByDesc(PayableEntity::getBizDate).orderByDesc(PayableEntity::getId));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toPayableReport).toList()
        );
    }

    private PageResponse<FinanceSettlementReportResponse> listReceivables(FinanceSettlementReportQuery query) {
        Page<ReceivableEntity> page = new Page<>(normalizePageNo(query.getPageNo()), normalizePageSize(query.getPageSize()));
        Page<ReceivableEntity> result = receivableMapper.selectPage(page, receivableWrapper(query).orderByDesc(ReceivableEntity::getBizDate).orderByDesc(ReceivableEntity::getId));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toReceivableReport).toList()
        );
    }

    private PageResponse<FinanceSettlementReportResponse> listAllSettlements(FinanceSettlementReportQuery query) {
        long pageNo = normalizePageNo(query.getPageNo());
        long pageSize = normalizePageSize(query.getPageSize());
        long pageStart = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<PayableEntity> payableWrapper = payableWrapper(query);
        LambdaQueryWrapper<ReceivableEntity> receivableWrapper = receivableWrapper(query);
        long payableTotal = payableMapper.selectCount(payableWrapper);
        long receivableTotal = receivableMapper.selectCount(receivableWrapper);
        long total = payableTotal + receivableTotal;
        if (pageStart >= total) {
            return new PageResponse<>(pageNo, pageSize, total, List.of());
        }

        List<FinanceSettlementReportResponse> records = financeSettlementReportMapper.selectAllSettlementPage(
                payableWrapper(query, "payableWrapper"),
                receivableWrapper(query, "receivableWrapper"),
                pageSize,
                pageStart
        );
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(FinanceSettlementReportQuery query) {
        return payableWrapper(query, null);
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(FinanceSettlementReportQuery query, String paramAlias) {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(paramAlias)) {
            wrapper.setParamAlias(paramAlias);
        }
        wrapper.eq(PayableEntity::getDeletedFlag, 0);
        if (query.getPartnerId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, query.getPartnerId());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(PayableEntity::getSourceType, sourceType);
        }
        if (query.getBizDateFrom() != null) {
            wrapper.ge(PayableEntity::getBizDate, query.getBizDateFrom());
        }
        if (query.getBizDateTo() != null) {
            wrapper.le(PayableEntity::getBizDate, query.getBizDateTo());
        }
        return financeSettlementScopeSupport.applyPayableScope(wrapper);
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(FinanceSettlementReportQuery query) {
        return receivableWrapper(query, null);
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(FinanceSettlementReportQuery query, String paramAlias) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(paramAlias)) {
            wrapper.setParamAlias(paramAlias);
        }
        wrapper.eq(ReceivableEntity::getDeletedFlag, 0);
        if (query.getPartnerId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, query.getPartnerId());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReceivableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(ReceivableEntity::getSourceType, sourceType);
        }
        if (query.getBizDateFrom() != null) {
            wrapper.ge(ReceivableEntity::getBizDate, query.getBizDateFrom());
        }
        if (query.getBizDateTo() != null) {
            wrapper.le(ReceivableEntity::getBizDate, query.getBizDateTo());
        }
        return financeSettlementScopeSupport.applyReceivableScope(wrapper);
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> inventoryBalanceWrapper(InventoryBalanceReportQuery query) {
        return inventoryBalanceWrapper(query, true);
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> inventoryBalanceExportWrapper(InventoryBalanceReportQuery query, boolean ordered) {
        InventoryBalanceReportQuery safeQuery = query == null ? new InventoryBalanceReportQuery() : query;
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = inventoryBalanceWrapper(safeQuery, ordered);
        return dataScopeService.applyInventoryBalanceScope(wrapper, currentSnapshot());
    }

    private LambdaQueryWrapper<InventoryBalanceEntity> inventoryBalanceWrapper(InventoryBalanceReportQuery query, boolean ordered) {
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, currentCompanyId())
                .eq(InventoryBalanceEntity::getAccountBookId, currentAccountBookId());
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
        return wrapper;
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionWrapper(InventoryTransactionReportQuery query) {
        return inventoryTransactionWrapper(query, true);
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionExportWrapper(InventoryTransactionReportQuery query, boolean ordered) {
        InventoryTransactionReportQuery safeQuery = query == null ? new InventoryTransactionReportQuery() : query;
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = inventoryTransactionWrapper(safeQuery, ordered);
        return dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionWrapper(InventoryTransactionReportQuery query, boolean ordered) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, currentCompanyId())
                .eq(InventoryTransactionEntity::getAccountBookId, currentAccountBookId());
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
        return wrapper;
    }

    private Long currentCompanyId() {
        return currentUserContext.requireCurrentUser().companyId();
    }

    private Long currentAccountBookId() {
        return currentUserContext.requireCurrentUser().accountBookId();
    }

    private DataScopeSnapshot currentSnapshot() {
        return currentUserContext.requirePrincipal().dataScopeSnapshot();
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

    private FinanceSettlementReportResponse toPayableReport(PayableEntity entity) {
        return new FinanceSettlementReportResponse(
                entity.getId(),
                "PAYABLE",
                entity.getPayableNo(),
                entity.getSupplierId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceNo(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus()
        );
    }

    private FinanceSettlementReportResponse toReceivableReport(ReceivableEntity entity) {
        return new FinanceSettlementReportResponse(
                entity.getId(),
                "RECEIVABLE",
                entity.getReceivableNo(),
                entity.getCustomerId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceNo(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus()
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
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

    private void streamAllFinanceSettlements(
            FinanceSettlementReportQuery query,
            Consumer<FinanceSettlementReportResponse> consumer
    ) {
        long batchSize = exportBatchSize();
        long pageStart = 0;
        while (true) {
            List<FinanceSettlementReportResponse> records = financeSettlementReportMapper.selectAllSettlementPage(
                    payableWrapper(query, "payableWrapper"),
                    receivableWrapper(query, "receivableWrapper"),
                    batchSize,
                    pageStart
            );
            if (records.isEmpty()) {
                return;
            }
            records.forEach(consumer);
            if (records.size() < batchSize) {
                return;
            }
            pageStart += batchSize;
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
