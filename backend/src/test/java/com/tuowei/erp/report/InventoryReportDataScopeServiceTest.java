package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.report.service.InventoryReportDataScopeService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportDataScopeServiceTest {

    private static final DataScopeSnapshot SNAPSHOT =
            new DataScopeSnapshot(false, false, false, false, Set.of(301L, 302L));

    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final InventoryReportDataScopeService service = new InventoryReportDataScopeService(dataScopeService);

    @Test
    void delegatesInventoryBalanceScopeToCompatibilityPolicy() {
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        when(dataScopeService.applyInventoryBalanceScope(wrapper, SNAPSHOT)).thenReturn(wrapper);

        assertThat(service.applyInventoryBalanceScope(wrapper, SNAPSHOT)).isSameAs(wrapper);
        verify(dataScopeService).applyInventoryBalanceScope(wrapper, SNAPSHOT);
    }

    @Test
    void delegatesInventoryTransactionScopeToCompatibilityPolicy() {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<>();
        when(dataScopeService.applyInventoryTransactionScope(wrapper, SNAPSHOT)).thenReturn(wrapper);

        assertThat(service.applyInventoryTransactionScope(wrapper, SNAPSHOT)).isSameAs(wrapper);
        verify(dataScopeService).applyInventoryTransactionScope(wrapper, SNAPSHOT);
    }
}
