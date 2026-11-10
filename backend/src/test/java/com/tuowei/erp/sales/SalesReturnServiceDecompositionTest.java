package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.returnorder.service.SalesReturnCommandService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnNumberService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnPostingService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnUpdateRequest;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesReturnServiceDecompositionTest {

    @Test
    void salesReturnServiceKeepsReadSideSecurityBehindQueryService() {
        assertThat(constructorDependencies(SalesReturnService.class))
                .contains(SalesReturnQueryService.class, SalesReturnPostingService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        SalesOrderMapper.class,
                        SalesOrderLineMapper.class,
                        InventoryTransactionMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        FinancePostingService.class,
                        AccountPeriodGuard.class
                );
        assertThat(constructorDependencies(SalesReturnQueryService.class))
                .doesNotContain(SalesReturnService.class);
        assertThat(constructorDependencies(SalesReturnPostingService.class))
                .doesNotContain(
                        SalesReturnService.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        ProductValidator.class,
                        SalesReturnNumberService.class
                );
    }

    @Test
    void returnPostingKeepsRequiredWriteTransactionsOnFacadeAndCollaborator() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SalesReturnService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(SalesReturnPostingService.class.getDeclaredMethod("post", Long.class));
    }

    @Test
    void facadeDependsOnlyOnQueryPostingAndCommandCollaborators() {
        assertThat(constructorDependencies(SalesReturnService.class))
                .containsExactlyInAnyOrder(
                        SalesReturnQueryService.class,
                        SalesReturnPostingService.class,
                        SalesReturnCommandService.class
                );
        assertThat(constructorDependencies(SalesReturnCommandService.class))
                .contains(SalesReturnQueryService.class)
                .doesNotContain(SalesReturnService.class, SalesReturnPostingService.class);
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        SalesReturnQueryService queryService = mock(SalesReturnQueryService.class);
        SalesReturnPostingService postingService = mock(SalesReturnPostingService.class);
        SalesReturnCommandService commandService = mock(SalesReturnCommandService.class);
        SalesReturnService service = new SalesReturnService(queryService, postingService, commandService);

        SalesReturnCreateRequest createRequest = mock(SalesReturnCreateRequest.class);
        SalesReturnUpdateRequest updateRequest = mock(SalesReturnUpdateRequest.class);
        SalesReturnPageQuery pageQuery = mock(SalesReturnPageQuery.class);
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        @SuppressWarnings("unchecked")
        PageResponse<SalesReturnResponse> pageResponse = mock(PageResponse.class);

        when(commandService.create(createRequest)).thenReturn(response);
        when(queryService.list(pageQuery)).thenReturn(pageResponse);
        when(queryService.getById(7L)).thenReturn(response);
        when(commandService.update(7L, updateRequest)).thenReturn(response);
        when(commandService.cancel(7L)).thenReturn(response);
        when(postingService.post(7L)).thenReturn(response);

        assertThat(service.create(createRequest)).isSameAs(response);
        assertThat(service.list(pageQuery)).isSameAs(pageResponse);
        assertThat(service.getById(7L)).isSameAs(response);
        assertThat(service.update(7L, updateRequest)).isSameAs(response);
        assertThat(service.cancel(7L)).isSameAs(response);
        assertThat(service.post(7L)).isSameAs(response);

        verify(commandService).create(createRequest);
        verify(queryService).list(pageQuery);
        verify(queryService).getById(7L);
        verify(commandService).update(7L, updateRequest);
        verify(commandService).cancel(7L);
        verify(postingService).post(7L);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SalesReturnService.class, SalesReturnQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", SalesReturnPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SalesReturnService.class, SalesReturnCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("create", SalesReturnCreateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("update", Long.class, SalesReturnUpdateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("cancel", Long.class));
        }
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
