package com.tuowei.erp.finance.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FinanceSettlementScopeSupport {

    private final FinanceSettlementQueryScopeService queryScopeService;
    private final FinanceSettlementDetailAccessService detailAccessService;

    @Autowired
    public FinanceSettlementScopeSupport(
            FinanceSettlementQueryScopeService queryScopeService,
            FinanceSettlementDetailAccessService detailAccessService
    ) {
        this.queryScopeService = queryScopeService;
        this.detailAccessService = detailAccessService;
    }

    /** Keeps direct construction in existing non-Spring tests and integrations compatible. */
    public FinanceSettlementScopeSupport(
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesReturnMapper salesReturnMapper
    ) {
        this(
                new FinanceSettlementQueryScopeService(
                        new FinanceSettlementScopeContextResolver(currentUserContext, scopedUserResolver)),
                new FinanceSettlementDetailAccessService(
                        new FinanceSettlementScopeContextResolver(currentUserContext, scopedUserResolver),
                        dataScopeService,
                        purchaseReceiptMapper,
                        purchaseReturnMapper,
                        salesDeliveryMapper,
                        salesReturnMapper)
        );
    }

    public LambdaQueryWrapper<ReceivableEntity> applyReceivableScope(LambdaQueryWrapper<ReceivableEntity> wrapper) {
        return queryScopeService.applyReceivableScope(wrapper);
    }

    public LambdaQueryWrapper<PayableEntity> applyPayableScope(LambdaQueryWrapper<PayableEntity> wrapper) {
        return queryScopeService.applyPayableScope(wrapper);
    }

    public void assertCanViewReceivable(ReceivableEntity entity) {
        detailAccessService.assertCanViewReceivable(entity);
    }

    public void assertCanViewPayable(PayableEntity entity) {
        detailAccessService.assertCanViewPayable(entity);
    }
}
