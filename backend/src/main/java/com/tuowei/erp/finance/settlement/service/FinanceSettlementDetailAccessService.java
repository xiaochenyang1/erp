package com.tuowei.erp.finance.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeContextResolver.ResolvedScope;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class FinanceSettlementDetailAccessService {

    private static final List<String> SALES_SOURCE_TYPES = List.of("SALES_DELIVERY", "SALES_RETURN");
    private static final List<String> PURCHASE_SOURCE_TYPES = List.of("PURCHASE_RECEIPT", "PURCHASE_RETURN");

    private final FinanceSettlementScopeContextResolver scopeContextResolver;
    private final DataScopeService dataScopeService;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesReturnMapper salesReturnMapper;

    public FinanceSettlementDetailAccessService(
            FinanceSettlementScopeContextResolver scopeContextResolver,
            DataScopeService dataScopeService,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesReturnMapper salesReturnMapper
    ) {
        this.scopeContextResolver = scopeContextResolver;
        this.dataScopeService = dataScopeService;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesReturnMapper = salesReturnMapper;
    }

    public void assertCanViewReceivable(ReceivableEntity entity) {
        ResolvedScope scopedUsers = scopeContextResolver.resolve();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (!Objects.equals(entity.getAccountBookId(), scopedUsers.currentUser().accountBookId())) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new AccessDeniedException("无权访问该应收记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("SALES_DELIVERY".equals(entity.getSourceType())
                && canViewSalesDelivery(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if ("SALES_RETURN".equals(entity.getSourceType())
                && canViewSalesReturn(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if (!SALES_SOURCE_TYPES.contains(entity.getSourceType())
                && scopeContextResolver.visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应收记录");
    }

    public void assertCanViewPayable(PayableEntity entity) {
        ResolvedScope scopedUsers = scopeContextResolver.resolve();
        if (!Objects.equals(entity.getCompanyId(), scopedUsers.currentUser().companyId())) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (!Objects.equals(entity.getAccountBookId(), scopedUsers.currentUser().accountBookId())) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new AccessDeniedException("无权访问该应付记录");
        }
        if (scopedUsers.snapshot().hasAllScope()) {
            return;
        }
        if ("PURCHASE_RECEIPT".equals(entity.getSourceType())
                && canViewPurchaseReceipt(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if ("PURCHASE_RETURN".equals(entity.getSourceType())
                && canViewPurchaseReturn(entity.getSourceId(), scopedUsers)) {
            return;
        }
        if (!PURCHASE_SOURCE_TYPES.contains(entity.getSourceType())
                && scopeContextResolver.visibleCreatorIds(scopedUsers).contains(entity.getCreatedBy())) {
            return;
        }
        throw new AccessDeniedException("无权访问该应付记录");
    }

    private boolean canViewSalesDelivery(Long sourceId, ResolvedScope scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = salesDeliveryMapper.selectCount(dataScopeService.applySalesDeliveryScope(
                new LambdaQueryWrapper<SalesDeliveryEntity>()
                        .eq(SalesDeliveryEntity::getId, sourceId)
                        .eq(SalesDeliveryEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewSalesReturn(Long sourceId, ResolvedScope scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = salesReturnMapper.selectCount(dataScopeService.applySalesReturnScope(
                new LambdaQueryWrapper<SalesReturnEntity>()
                        .eq(SalesReturnEntity::getId, sourceId)
                        .eq(SalesReturnEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewPurchaseReceipt(Long sourceId, ResolvedScope scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = purchaseReceiptMapper.selectCount(dataScopeService.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<PurchaseReceiptEntity>()
                        .eq(PurchaseReceiptEntity::getId, sourceId)
                        .eq(PurchaseReceiptEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }

    private boolean canViewPurchaseReturn(Long sourceId, ResolvedScope scopedUsers) {
        if (sourceId == null) {
            return false;
        }
        Long count = purchaseReturnMapper.selectCount(dataScopeService.applyPurchaseReturnScope(
                new LambdaQueryWrapper<PurchaseReturnEntity>()
                        .eq(PurchaseReturnEntity::getId, sourceId)
                        .eq(PurchaseReturnEntity::getDeletedFlag, 0),
                scopedUsers.currentUser(),
                scopedUsers.snapshot(),
                scopedUsers.deptUserIds(),
                scopedUsers.postUserIds()
        ));
        return count != null && count > 0;
    }
}
