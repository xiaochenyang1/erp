package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class BusinessTraceDataScopeService {

    private final DataScopeService dataScopeService;

    public BusinessTraceDataScopeService(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    public LambdaQueryWrapper<SalesOrderEntity> salesOrderScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applySalesOrderScope(
                new LambdaQueryWrapper<>(),
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    public LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applyPurchaseOrderScope(
                new LambdaQueryWrapper<>(),
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    public LambdaQueryWrapper<SalesDeliveryEntity> salesDeliveryScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applySalesDeliveryScope(
                new LambdaQueryWrapper<>(),
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    public LambdaQueryWrapper<PurchaseReceiptEntity> purchaseReceiptScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<>(),
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    public LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot
    ) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = tenantScopedInventoryTransaction(currentUser);
        return dataScopeService.applyInventoryTransactionScope(wrapper, snapshot);
    }

    public Optional<LambdaQueryWrapper<ReceivableEntity>> receivableQuery(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            String keyword,
            Set<String> knownBizNos
    ) {
        if (skipRestrictedSecondaryQuery(snapshot, knownBizNos)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<ReceivableEntity> wrapper = tenantScopedReceivable(currentUser)
                .eq(ReceivableEntity::getDeletedFlag, 0);
        if (snapshot.hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(ReceivableEntity::getReceivableNo, keyword)
                        .or()
                        .like(ReceivableEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(ReceivableEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            wrapper.in(ReceivableEntity::getSourceNo, knownBizNos);
        }
        return Optional.of(wrapper);
    }

    public Optional<LambdaQueryWrapper<PayableEntity>> payableQuery(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            String keyword,
            Set<String> knownBizNos
    ) {
        if (skipRestrictedSecondaryQuery(snapshot, knownBizNos)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PayableEntity> wrapper = tenantScopedPayable(currentUser)
                .eq(PayableEntity::getDeletedFlag, 0);
        if (snapshot.hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(PayableEntity::getPayableNo, keyword)
                        .or()
                        .like(PayableEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(PayableEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            wrapper.in(PayableEntity::getSourceNo, knownBizNos);
        }
        return Optional.of(wrapper);
    }

    public Optional<LambdaQueryWrapper<WorkflowTaskEntity>> workflowTaskQuery(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            String keyword,
            Set<String> knownBizNos
    ) {
        if (skipRestrictedSecondaryQuery(snapshot, knownBizNos)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<WorkflowTaskEntity> wrapper = tenantScopedWorkflowTask(currentUser);
        if (snapshot.hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(WorkflowTaskEntity::getBusinessNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(WorkflowTaskEntity::getBusinessNo, knownBizNos);
                }
            });
        } else {
            wrapper.in(WorkflowTaskEntity::getBusinessNo, knownBizNos);
        }
        return Optional.of(wrapper);
    }

    public Optional<LambdaQueryWrapper<OperationLogEntity>> operationLogQuery(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            String keyword,
            Set<String> knownBizNos
    ) {
        if (skipRestrictedSecondaryQuery(snapshot, knownBizNos)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<OperationLogEntity> wrapper = tenantScopedOperationLog(currentUser);
        if (snapshot.hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(OperationLogEntity::getBizNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(OperationLogEntity::getBizNo, knownBizNos);
                }
            });
        } else {
            wrapper.in(OperationLogEntity::getBizNo, knownBizNos);
        }
        return Optional.of(wrapper);
    }

    public Optional<LambdaQueryWrapper<ExceptionTicketEntity>> exceptionTicketQuery(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            String keyword,
            Set<String> knownBizNos
    ) {
        if (skipRestrictedSecondaryQuery(snapshot, knownBizNos)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<ExceptionTicketEntity> wrapper = tenantScopedExceptionTicket(currentUser)
                .eq(ExceptionTicketEntity::getDeletedFlag, 0);
        if (snapshot.hasAllScope()) {
            wrapper.and(nested -> {
                nested.like(ExceptionTicketEntity::getTicketNo, keyword)
                        .or()
                        .like(ExceptionTicketEntity::getSourceNo, keyword);
                if (!knownBizNos.isEmpty()) {
                    nested.or().in(ExceptionTicketEntity::getSourceNo, knownBizNos);
                }
            });
        } else {
            wrapper.in(ExceptionTicketEntity::getSourceNo, knownBizNos);
        }
        return Optional.of(wrapper);
    }

    private boolean skipRestrictedSecondaryQuery(DataScopeSnapshot snapshot, Set<String> knownBizNos) {
        return !snapshot.hasAllScope() && knownBizNos.isEmpty();
    }

    private LambdaQueryWrapper<ReceivableEntity> tenantScopedReceivable(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, currentUser.companyId())
                .eq(ReceivableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<PayableEntity> tenantScopedPayable(CurrentUser currentUser) {
        return new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, currentUser.companyId())
                .eq(PayableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> tenantScopedInventoryTransaction(CurrentUser currentUser) {
        return new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> tenantScopedWorkflowTask(CurrentUser currentUser) {
        return new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getCompanyId, currentUser.companyId())
                .eq(WorkflowTaskEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<OperationLogEntity> tenantScopedOperationLog(CurrentUser currentUser) {
        return new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getCompanyId, currentUser.companyId())
                .eq(OperationLogEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<ExceptionTicketEntity> tenantScopedExceptionTicket(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, currentUser.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, currentUser.accountBookId());
    }
}
