package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataScopeServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnSnapshotAndPolicyCollaborators() {
        assertThat(constructorDependencies(DataScopeService.class))
                .containsExactlyInAnyOrder(DataScopeSnapshotService.class, DataScopePolicyService.class);
        assertThat(constructorDependencies(DataScopePolicyService.class)).isEmpty();
        assertThat(constructorDependencies(DataScopeSnapshotService.class))
                .doesNotContain(DataScopeService.class, DataScopePolicyService.class);
    }

    @Test
    void facadeCoversEveryCollaboratorPublicApiAndAddsNothing() {
        Set<String> facadeApi = publicSignatures(DataScopeService.class);
        Set<String> snapshotApi = publicSignatures(DataScopeSnapshotService.class);
        Set<String> policyApi = publicSignatures(DataScopePolicyService.class);

        assertThat(snapshotApi).allMatch(facadeApi::contains);
        assertThat(policyApi).allMatch(facadeApi::contains);
        // Facade surface is exactly the union of the two collaborators' public APIs (none dropped, none added).
        assertThat(facadeApi).hasSize(snapshotApi.size() + policyApi.size());
    }

    @Test
    void facadeDelegatesSnapshotResolutionToSnapshotService() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        DataScopePolicyService policyService = mock(DataScopePolicyService.class);
        DataScopeService facade = new DataScopeService(snapshotService, policyService);

        DataScopeSnapshot snapshot = DataScopeSnapshot.none();
        when(snapshotService.buildSnapshot(7L, 1L, 2L)).thenReturn(snapshot);

        assertThat(facade.buildSnapshot(7L, 1L, 2L)).isSameAs(snapshot);
        verify(snapshotService).buildSnapshot(7L, 1L, 2L);
        verifyNoInteractions(policyService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void facadeDelegatesScopeAndGuardCallsToPolicyService() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        DataScopePolicyService policyService = mock(DataScopePolicyService.class);
        DataScopeService facade = new DataScopeService(snapshotService, policyService);

        CurrentUser currentUser = mock(CurrentUser.class);
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        Set<Long> deptUserIds = Set.of(11L);
        Set<Long> postUserIds = Set.of(22L);

        LambdaQueryWrapper<PurchaseOrderEntity> poWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<PurchaseOrderEntity> poResult = mock(LambdaQueryWrapper.class);
        when(policyService.applyPurchaseOrderScope(poWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(poResult);
        assertThat(facade.applyPurchaseOrderScope(poWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(poResult);
        verify(policyService).applyPurchaseOrderScope(poWrapper, currentUser, snapshot, deptUserIds, postUserIds);

        LambdaQueryWrapper<InventoryBalanceEntity> balWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryBalanceEntity> balResult = mock(LambdaQueryWrapper.class);
        when(policyService.applyInventoryBalanceScope(balWrapper, snapshot)).thenReturn(balResult);
        assertThat(facade.applyInventoryBalanceScope(balWrapper, snapshot)).isSameAs(balResult);
        verify(policyService).applyInventoryBalanceScope(balWrapper, snapshot);

        ProductionOrderEntity productionOrder = new ProductionOrderEntity();
        facade.assertCanViewProductionOrder(productionOrder, currentUser, snapshot, 3L, 4L);
        verify(policyService).assertCanViewProductionOrder(productionOrder, currentUser, snapshot, 3L, 4L);

        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        facade.assertCanViewInventoryBalance(balance, snapshot);
        verify(policyService).assertCanViewInventoryBalance(balance, snapshot);

        verifyNoInteractions(snapshotService);
    }

    @Test
    void dataScopeStaysTransactionNeutralAcrossFacadeAndCollaborators() {
        assertThat(declaresAnyTransactional(DataScopeService.class)).isFalse();
        assertThat(declaresAnyTransactional(DataScopeSnapshotService.class)).isFalse();
        assertThat(declaresAnyTransactional(DataScopePolicyService.class)).isFalse();
    }

    private static Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private static Set<String> publicSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()
                        + Arrays.toString(method.getParameterTypes())
                        + "->" + method.getReturnType().getName())
                .collect(Collectors.toSet());
    }

    private static boolean declaresAnyTransactional(Class<?> type) {
        if (type.isAnnotationPresent(Transactional.class)) {
            return true;
        }
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(Transactional.class));
    }
}
