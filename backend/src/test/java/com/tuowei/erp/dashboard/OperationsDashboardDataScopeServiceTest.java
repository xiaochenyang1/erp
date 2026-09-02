package com.tuowei.erp.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.dashboard.service.OperationsDashboardDataScopeService;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationsDashboardDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );

    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final OperationsDashboardDataScopeService service =
            new OperationsDashboardDataScopeService(dataScopeService);

    @Test
    void delegatesDocumentScopesToCompatibilityPolicy() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, true, Set.of());
        ScopedUserResolver.ScopedUserIds scopedUserIds =
                new ScopedUserResolver.ScopedUserIds(Set.of(21L), Set.of(31L));
        LambdaQueryWrapper<PurchaseOrderEntity> purchaseWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<SalesOrderEntity> salesWrapper = new LambdaQueryWrapper<>();
        when(dataScopeService.applyPurchaseOrderScope(
                purchaseWrapper, CURRENT_USER, snapshot, Set.of(21L), Set.of(31L)))
                .thenReturn(purchaseWrapper);
        when(dataScopeService.applySalesOrderScope(
                salesWrapper, CURRENT_USER, snapshot, Set.of(21L), Set.of(31L)))
                .thenReturn(salesWrapper);

        assertThat(service.applyPurchaseOrderScope(
                purchaseWrapper, CURRENT_USER, snapshot, scopedUserIds)).isSameAs(purchaseWrapper);
        assertThat(service.applySalesOrderScope(
                salesWrapper, CURRENT_USER, snapshot, scopedUserIds)).isSameAs(salesWrapper);
        verify(dataScopeService).applyPurchaseOrderScope(
                purchaseWrapper, CURRENT_USER, snapshot, Set.of(21L), Set.of(31L));
        verify(dataScopeService).applySalesOrderScope(
                salesWrapper, CURRENT_USER, snapshot, Set.of(21L), Set.of(31L));
    }

    @Test
    void allScopeUsesNullAggregationFilters() {
        OperationsDashboardDataScopeService.AggregationScope scope = service.resolveAggregationScope(
                CURRENT_USER,
                DataScopeSnapshot.all(),
                new ScopedUserResolver.ScopedUserIds(Set.of(21L), Set.of(31L))
        );

        assertThat(scope.visibleCreatorIds()).isNull();
        assertThat(scope.warehouseIds()).isNull();
    }

    @Test
    void emptyScopeUsesEmptyAggregationFilters() {
        OperationsDashboardDataScopeService.AggregationScope scope = service.resolveAggregationScope(
                CURRENT_USER,
                DataScopeSnapshot.none(),
                new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of())
        );

        assertThat(scope.visibleCreatorIds()).isEmpty();
        assertThat(scope.warehouseIds()).isEmpty();
    }

    @Test
    void aggregationScopeCombinesCreatorAndWarehouseVisibility() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, true, Set.of(601L, 602L));

        OperationsDashboardDataScopeService.AggregationScope scope = service.resolveAggregationScope(
                CURRENT_USER,
                snapshot,
                new ScopedUserResolver.ScopedUserIds(Set.of(21L, 22L), Set.of(31L, 32L))
        );

        assertThat(scope.visibleCreatorIds())
                .containsExactlyInAnyOrder(CURRENT_USER.userId(), 21L, 22L, 31L, 32L);
        assertThat(scope.warehouseIds()).containsExactlyInAnyOrder(601L, 602L);
    }
}
