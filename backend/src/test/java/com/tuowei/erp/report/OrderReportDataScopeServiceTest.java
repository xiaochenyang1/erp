package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.report.service.OrderReportDataScopeService;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReportDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9201L,
            101L,
            202L,
            11L,
            12L,
            "order_report_user",
            "Order Report User"
    );
    private static final DataScopeSnapshot SNAPSHOT =
            new DataScopeSnapshot(false, true, true, true, Set.of());
    private static final ScopedUserResolver.ScopedUserIds SCOPED_USER_IDS =
            new ScopedUserResolver.ScopedUserIds(Set.of(9202L), Set.of(9203L));

    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final OrderReportDataScopeService service = new OrderReportDataScopeService(dataScopeService);

    @Test
    void delegatesPurchaseOrderScopeToCompatibilityPolicy() {
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<>();
        when(dataScopeService.applyPurchaseOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, Set.of(9202L), Set.of(9203L)))
                .thenReturn(wrapper);

        assertThat(service.applyPurchaseOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, SCOPED_USER_IDS)).isSameAs(wrapper);
        verify(dataScopeService).applyPurchaseOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, Set.of(9202L), Set.of(9203L));
    }

    @Test
    void delegatesSalesOrderScopeToCompatibilityPolicy() {
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<>();
        when(dataScopeService.applySalesOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, Set.of(9202L), Set.of(9203L)))
                .thenReturn(wrapper);

        assertThat(service.applySalesOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, SCOPED_USER_IDS)).isSameAs(wrapper);
        verify(dataScopeService).applySalesOrderScope(
                wrapper, CURRENT_USER, SNAPSHOT, Set.of(9202L), Set.of(9203L));
    }
}
