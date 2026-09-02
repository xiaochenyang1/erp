package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.report.service.FinanceSettlementReportDataScopeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceSettlementReportDataScopeServiceTest {

    private final FinanceSettlementScopeSupport financeSettlementScopeSupport =
            mock(FinanceSettlementScopeSupport.class);
    private final FinanceSettlementReportDataScopeService service =
            new FinanceSettlementReportDataScopeService(financeSettlementScopeSupport);

    @Test
    void delegatesPayableScopeToSharedFinancePolicy() {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        when(financeSettlementScopeSupport.applyPayableScope(wrapper)).thenReturn(wrapper);

        assertThat(service.applyPayableScope(wrapper)).isSameAs(wrapper);
        verify(financeSettlementScopeSupport).applyPayableScope(wrapper);
    }

    @Test
    void delegatesReceivableScopeToSharedFinancePolicy() {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        when(financeSettlementScopeSupport.applyReceivableScope(wrapper)).thenReturn(wrapper);

        assertThat(service.applyReceivableScope(wrapper)).isSameAs(wrapper);
        verify(financeSettlementScopeSupport).applyReceivableScope(wrapper);
    }
}
