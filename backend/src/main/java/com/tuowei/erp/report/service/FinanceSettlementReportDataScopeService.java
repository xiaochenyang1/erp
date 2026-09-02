package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import org.springframework.stereotype.Service;

@Service
public class FinanceSettlementReportDataScopeService {

    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;

    public FinanceSettlementReportDataScopeService(FinanceSettlementScopeSupport financeSettlementScopeSupport) {
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
    }

    public LambdaQueryWrapper<PayableEntity> applyPayableScope(LambdaQueryWrapper<PayableEntity> wrapper) {
        return financeSettlementScopeSupport.applyPayableScope(wrapper);
    }

    public LambdaQueryWrapper<ReceivableEntity> applyReceivableScope(LambdaQueryWrapper<ReceivableEntity> wrapper) {
        return financeSettlementScopeSupport.applyReceivableScope(wrapper);
    }
}
