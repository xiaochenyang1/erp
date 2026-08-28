package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import com.tuowei.erp.report.web.BusinessTraceResponse;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BusinessTraceService {

    private static final int SOURCE_LIMIT = 20;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final OperationLogMapper operationLogMapper;
    private final ExceptionTicketMapper exceptionTicketMapper;
    private final Clock clock;
    private final BusinessTraceAssemblyService assemblyService;

    public BusinessTraceService(
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            WorkflowTaskMapper workflowTaskMapper,
            OperationLogMapper operationLogMapper,
            ExceptionTicketMapper exceptionTicketMapper,
            Clock clock,
            BusinessTraceAssemblyService assemblyService
    ) {
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        this.operationLogMapper = operationLogMapper;
        this.exceptionTicketMapper = exceptionTicketMapper;
        this.clock = clock;
        this.assemblyService = assemblyService;
    }

    @Transactional(readOnly = true)
    public BusinessTraceResponse trace(BusinessTraceQuery query) {
        String keyword = normalizeKeyword(query);
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        if (!StringUtils.hasText(keyword)) {
            return assemblyService.empty(keyword, generatedAt);
        }

        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        ScopedUsers scopedUsers = scopedUsers(currentUser);
        List<SalesOrderEntity> salesOrders = listSalesOrders(keyword, scopedUsers);
        List<PurchaseOrderEntity> purchaseOrders = listPurchaseOrders(keyword, scopedUsers);

        Set<Long> salesOrderIds = ids(salesOrders);
        Set<Long> purchaseOrderIds = ids(purchaseOrders);
        Set<String> knownBizNos = new LinkedHashSet<>();
        salesOrders.stream().map(SalesOrderEntity::getOrderNo).forEach(knownBizNos::add);
        purchaseOrders.stream().map(PurchaseOrderEntity::getOrderNo).forEach(knownBizNos::add);

        List<SalesDeliveryEntity> salesDeliveries = listSalesDeliveries(keyword, salesOrderIds, scopedUsers);
        List<PurchaseReceiptEntity> purchaseReceipts = listPurchaseReceipts(keyword, purchaseOrderIds, scopedUsers);
        salesDeliveries.stream().map(SalesDeliveryEntity::getDeliveryNo).forEach(knownBizNos::add);
        purchaseReceipts.stream().map(PurchaseReceiptEntity::getReceiptNo).forEach(knownBizNos::add);

        List<ReceivableEntity> receivables = listReceivables(keyword, knownBizNos, scopedUsers);
        List<PayableEntity> payables = listPayables(keyword, knownBizNos, scopedUsers);
        receivables.stream().map(ReceivableEntity::getReceivableNo).forEach(knownBizNos::add);
        payables.stream().map(PayableEntity::getPayableNo).forEach(knownBizNos::add);

        List<InventoryTransactionEntity> inventoryTransactions = listInventoryTransactions(keyword, knownBizNos, scopedUsers);
        inventoryTransactions.stream().map(InventoryTransactionEntity::getBizNo).forEach(knownBizNos::add);

        List<WorkflowTaskEntity> workflowTasks = listWorkflowTasks(keyword, knownBizNos, scopedUsers);
        List<OperationLogEntity> operationLogs = listOperationLogs(keyword, knownBizNos, scopedUsers);
        List<ExceptionTicketEntity> exceptionTickets = listExceptionTickets(keyword, knownBizNos, scopedUsers);

        return assemblyService.assemble(
                keyword,
                generatedAt,
                new BusinessTraceAssemblyService.TraceData(
                        salesOrders,
                        purchaseOrders,
                        salesDeliveries,
                        purchaseReceipts,
                        receivables,
                        payables,
                        inventoryTransactions,
                        workflowTasks,
                        operationLogs,
                        exceptionTickets
                )
        );
    }

    private List<SalesOrderEntity> listSalesOrders(String keyword, ScopedUsers scopedUsers) {
        return salesOrderMapper.selectList(salesOrderWrapper(scopedUsers)
                .eq(SalesOrderEntity::getDeletedFlag, 0)
                .like(SalesOrderEntity::getOrderNo, keyword)
                .orderByDesc(SalesOrderEntity::getOrderDate)
                .orderByDesc(SalesOrderEntity::getId)
                .last(limitSql()));
    }

    private List<PurchaseOrderEntity> listPurchaseOrders(String keyword, ScopedUsers scopedUsers) {
        return purchaseOrderMapper.selectList(purchaseOrderWrapper(scopedUsers)
                .eq(PurchaseOrderEntity::getDeletedFlag, 0)
                .like(PurchaseOrderEntity::getOrderNo, keyword)
                .orderByDesc(PurchaseOrderEntity::getOrderDate)
                .orderByDesc(PurchaseOrderEntity::getId)
                .last(limitSql()));
    }

    private List<SalesDeliveryEntity> listSalesDeliveries(String keyword, Set<Long> orderIds, ScopedUsers scopedUsers) {
        LambdaQueryWrapper<SalesDeliveryEntity> wrapper = salesDeliveryWrapper(scopedUsers)
                .eq(SalesDeliveryEntity::getDeletedFlag, 0);
        if (orderIds.isEmpty()) {
            wrapper.like(SalesDeliveryEntity::getDeliveryNo, keyword);
        } else {
            wrapper.and(nested -> nested.like(SalesDeliveryEntity::getDeliveryNo, keyword)
                    .or()
                    .in(SalesDeliveryEntity::getOrderId, orderIds));
        }
        return salesDeliveryMapper.selectList(dataScopeService.applySalesDeliveryScope(
                        wrapper,
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds())
                .orderByDesc(SalesDeliveryEntity::getDeliveryDate)
                .orderByDesc(SalesDeliveryEntity::getId)
                .last(limitSql()));
    }

    private List<PurchaseReceiptEntity> listPurchaseReceipts(String keyword, Set<Long> orderIds, ScopedUsers scopedUsers) {
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = purchaseReceiptWrapper(scopedUsers)
                .eq(PurchaseReceiptEntity::getDeletedFlag, 0);
        if (orderIds.isEmpty()) {
            wrapper.like(PurchaseReceiptEntity::getReceiptNo, keyword);
        } else {
            wrapper.and(nested -> nested.like(PurchaseReceiptEntity::getReceiptNo, keyword)
                    .or()
                    .in(PurchaseReceiptEntity::getOrderId, orderIds));
        }
        return purchaseReceiptMapper.selectList(dataScopeService.applyPurchaseReceiptScope(
                        wrapper,
                        scopedUsers.currentUser(),
                        scopedUsers.snapshot(),
                        scopedUsers.deptUserIds(),
                        scopedUsers.postUserIds())
                .orderByDesc(PurchaseReceiptEntity::getReceiptDate)
                .orderByDesc(PurchaseReceiptEntity::getId)
                .last(limitSql()));
    }

    private List<ReceivableEntity> listReceivables(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        if (!scopedUsers.snapshot().hasAllScope() && knownBizNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ReceivableEntity> wrapper = receivableWrapper(scopedUsers.currentUser())
                .eq(ReceivableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(ReceivableEntity::getReceivableNo, keyword)
                        .or()
                        .like(ReceivableEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(ReceivableEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            // A restricted user may only follow a visible source document. Direct
            // lookup by a hidden receivable number must not become a side channel.
            wrapper.in(ReceivableEntity::getSourceNo, knownBizNos);
        }
        return receivableMapper.selectList(wrapper
                .orderByDesc(ReceivableEntity::getBizDate)
                .orderByDesc(ReceivableEntity::getId)
                .last(limitSql()));
    }

    private List<PayableEntity> listPayables(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        if (!scopedUsers.snapshot().hasAllScope() && knownBizNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PayableEntity> wrapper = payableWrapper(scopedUsers.currentUser())
                .eq(PayableEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(PayableEntity::getPayableNo, keyword)
                        .or()
                        .like(PayableEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(PayableEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            // A restricted user may only follow a visible source document. Direct
            // lookup by a hidden payable number must not become a side channel.
            wrapper.in(PayableEntity::getSourceNo, knownBizNos);
        }
        return payableMapper.selectList(wrapper
                .orderByDesc(PayableEntity::getBizDate)
                .orderByDesc(PayableEntity::getId)
                .last(limitSql()));
    }

    private List<InventoryTransactionEntity> listInventoryTransactions(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = inventoryTransactionWrapper(scopedUsers.currentUser());
        wrapper.and(nested -> {
            nested.like(InventoryTransactionEntity::getBizNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(InventoryTransactionEntity::getBizNo, knownBizNos);
            }
        });
        return inventoryTransactionMapper.selectList(dataScopeService.applyInventoryTransactionScope(
                        wrapper,
                        scopedUsers.snapshot())
                .orderByDesc(InventoryTransactionEntity::getOccurredTime)
                .orderByDesc(InventoryTransactionEntity::getId)
                .last(limitSql()));
    }

    private List<WorkflowTaskEntity> listWorkflowTasks(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        if (!scopedUsers.snapshot().hasAllScope() && knownBizNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<WorkflowTaskEntity> wrapper = workflowTaskWrapper(scopedUsers.currentUser());
        if (scopedUsers.snapshot().hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(WorkflowTaskEntity::getBusinessNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(WorkflowTaskEntity::getBusinessNo, knownBizNos);
                }
            });
        } else {
            // Workflow tasks are secondary data; only visible source numbers may
            // be followed by a restricted user.
            wrapper.in(WorkflowTaskEntity::getBusinessNo, knownBizNos);
        }
        return workflowTaskMapper.selectList(wrapper
                .orderByDesc(WorkflowTaskEntity::getCreatedTime)
                .orderByDesc(WorkflowTaskEntity::getId)
                .last(limitSql()));
    }

    private List<OperationLogEntity> listOperationLogs(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        if (!scopedUsers.snapshot().hasAllScope() && knownBizNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<OperationLogEntity> wrapper = operationLogWrapper(scopedUsers.currentUser());
        if (scopedUsers.snapshot().hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(OperationLogEntity::getBizNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(OperationLogEntity::getBizNo, knownBizNos);
                }
            });
        } else {
            // Do not expose an operation log merely because its business number
            // was supplied directly. The source document must already be visible.
            wrapper.in(OperationLogEntity::getBizNo, knownBizNos);
        }
        return operationLogMapper.selectList(wrapper
                .orderByDesc(OperationLogEntity::getOperationTime)
                .orderByDesc(OperationLogEntity::getId)
                .last(limitSql()));
    }

    private List<ExceptionTicketEntity> listExceptionTickets(String keyword, Set<String> knownBizNos, ScopedUsers scopedUsers) {
        if (!scopedUsers.snapshot().hasAllScope() && knownBizNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExceptionTicketEntity> wrapper = exceptionTicketWrapper(scopedUsers.currentUser())
                .eq(ExceptionTicketEntity::getDeletedFlag, 0);
        if (scopedUsers.snapshot().hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(ExceptionTicketEntity::getTicketNo, keyword)
                        .or()
                        .like(ExceptionTicketEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(ExceptionTicketEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            // Exception tickets are secondary data; follow only visible sources.
            wrapper.in(ExceptionTicketEntity::getSourceNo, knownBizNos);
        }
        return exceptionTicketMapper.selectList(wrapper
                .orderByDesc(ExceptionTicketEntity::getUpdatedTime)
                .orderByDesc(ExceptionTicketEntity::getId)
                .last(limitSql()));
    }

    private LambdaQueryWrapper<SalesOrderEntity> salesOrderWrapper(ScopedUsers scopedUsers) {
        return dataScopeService.applySalesOrderScope(
                new LambdaQueryWrapper<SalesOrderEntity>(),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds());
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderWrapper(ScopedUsers scopedUsers) {
        return dataScopeService.applyPurchaseOrderScope(
                new LambdaQueryWrapper<PurchaseOrderEntity>(),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds());
    }

    private LambdaQueryWrapper<SalesDeliveryEntity> salesDeliveryWrapper(ScopedUsers scopedUsers) {
        return new LambdaQueryWrapper<SalesDeliveryEntity>()
                .eq(SalesDeliveryEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(SalesDeliveryEntity::getAccountBookId, scopedUsers.currentUser().accountBookId());
    }

    private LambdaQueryWrapper<PurchaseReceiptEntity> purchaseReceiptWrapper(ScopedUsers scopedUsers) {
        return new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getCompanyId, scopedUsers.currentUser().companyId())
                .eq(PurchaseReceiptEntity::getAccountBookId, scopedUsers.currentUser().accountBookId());
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, currentUser.companyId())
                .eq(ReceivableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, currentUser.companyId())
                .eq(PayableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> workflowTaskWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getCompanyId, currentUser.companyId())
                .eq(WorkflowTaskEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<OperationLogEntity> operationLogWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getCompanyId, currentUser.companyId())
                .eq(OperationLogEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<ExceptionTicketEntity> exceptionTicketWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, currentUser.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, currentUser.accountBookId());
    }

    private Set<Long> ids(List<? extends Object> entities) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object entity : entities) {
            if (entity instanceof SalesOrderEntity salesOrder && salesOrder.getId() != null) {
                ids.add(salesOrder.getId());
            } else if (entity instanceof PurchaseOrderEntity purchaseOrder && purchaseOrder.getId() != null) {
                ids.add(purchaseOrder.getId());
            }
        }
        return ids;
    }

    private String normalizeKeyword(BusinessTraceQuery query) {
        if (query == null || !StringUtils.hasText(query.getKeyword())) {
            return "";
        }
        return query.getKeyword().trim();
    }

    private String limitSql() {
        return "limit " + SOURCE_LIMIT;
    }

    private ScopedUsers scopedUsers(CurrentUser currentUser) {
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds ids = scopedUserResolver.resolve(currentUser, snapshot);
        return new ScopedUsers(currentUser, snapshot, ids.deptUserIds(), ids.postUserIds());
    }

    private record ScopedUsers(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
    }
}
