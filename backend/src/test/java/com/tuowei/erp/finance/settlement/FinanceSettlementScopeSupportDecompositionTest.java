package com.tuowei.erp.finance.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementDetailAccessService;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementQueryScopeService;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeContextResolver;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceSettlementScopeSupportDecompositionTest {

    @Test
    void springFacadeDependsOnlyOnSeparatedPolicies() {
        assertThat(autowiredConstructorDependencies(FinanceSettlementScopeSupport.class))
                .containsExactlyInAnyOrder(
                        FinanceSettlementQueryScopeService.class,
                        FinanceSettlementDetailAccessService.class
                );
        assertThat(hasMapperField(FinanceSettlementScopeSupport.class)).isFalse();
    }

    @Test
    void queryPolicyHasNoPersistenceDependencyAndDetailPolicyOwnsSourceMappers() {
        assertThat(constructorDependencies(FinanceSettlementQueryScopeService.class))
                .containsExactly(FinanceSettlementScopeContextResolver.class);
        assertThat(hasMapperField(FinanceSettlementQueryScopeService.class)).isFalse();
        assertThat(constructorDependencies(FinanceSettlementDetailAccessService.class))
                .containsExactlyInAnyOrder(
                        FinanceSettlementScopeContextResolver.class,
                        DataScopeService.class,
                        PurchaseReceiptMapper.class,
                        PurchaseReturnMapper.class,
                        SalesDeliveryMapper.class,
                        SalesReturnMapper.class
                );
        assertThat(constructorDependencies(FinanceSettlementScopeContextResolver.class))
                .containsExactlyInAnyOrder(CurrentUserContext.class, ScopedUserResolver.class);
    }

    @Test
    void previousSevenParameterConstructorRemainsAvailable() throws NoSuchMethodException {
        assertThat(FinanceSettlementScopeSupport.class.getDeclaredConstructor(
                CurrentUserContext.class,
                DataScopeService.class,
                ScopedUserResolver.class,
                PurchaseReceiptMapper.class,
                PurchaseReturnMapper.class,
                SalesDeliveryMapper.class,
                SalesReturnMapper.class
        )).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesAllScopeApis() {
        FinanceSettlementQueryScopeService queryScopeService = mock(FinanceSettlementQueryScopeService.class);
        FinanceSettlementDetailAccessService detailAccessService = mock(FinanceSettlementDetailAccessService.class);
        FinanceSettlementScopeSupport facade =
                new FinanceSettlementScopeSupport(queryScopeService, detailAccessService);
        LambdaQueryWrapper<ReceivableEntity> receivableWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<PayableEntity> payableWrapper = mock(LambdaQueryWrapper.class);
        ReceivableEntity receivable = mock(ReceivableEntity.class);
        PayableEntity payable = mock(PayableEntity.class);
        when(queryScopeService.applyReceivableScope(receivableWrapper)).thenReturn(receivableWrapper);
        when(queryScopeService.applyPayableScope(payableWrapper)).thenReturn(payableWrapper);

        assertThat(facade.applyReceivableScope(receivableWrapper)).isSameAs(receivableWrapper);
        assertThat(facade.applyPayableScope(payableWrapper)).isSameAs(payableWrapper);
        facade.assertCanViewReceivable(receivable);
        facade.assertCanViewPayable(payable);

        verify(queryScopeService).applyReceivableScope(receivableWrapper);
        verify(queryScopeService).applyPayableScope(payableWrapper);
        verify(detailAccessService).assertCanViewReceivable(receivable);
        verify(detailAccessService).assertCanViewPayable(payable);
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap((Constructor<?> constructor) -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private boolean hasMapperField(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> BaseMapper.class.isAssignableFrom(field.getType()));
    }
}
