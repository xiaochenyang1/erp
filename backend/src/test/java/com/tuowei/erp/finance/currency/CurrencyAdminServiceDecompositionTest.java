package com.tuowei.erp.finance.currency;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.mapper.AccountBookCurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.CurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.ExchangeRateMapper;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.CurrencyAdminCommandService;
import com.tuowei.erp.finance.currency.service.CurrencyAdminQueryService;
import com.tuowei.erp.finance.currency.service.BaseCurrencyDataGuard;
import com.tuowei.erp.finance.currency.service.CurrencyAdminService;
import com.tuowei.erp.finance.currency.web.BaseCurrencyRequest;
import com.tuowei.erp.finance.currency.web.CurrencyCreateRequest;
import com.tuowei.erp.finance.currency.web.CurrencyResponse;
import com.tuowei.erp.finance.currency.web.ExchangeRateRequest;
import com.tuowei.erp.finance.currency.web.ExchangeRateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyAdminServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(CurrencyAdminService.class))
                .containsExactlyInAnyOrder(CurrencyAdminQueryService.class, CurrencyAdminCommandService.class);
        assertThat(constructorDependencies(CurrencyAdminQueryService.class))
                .containsExactlyInAnyOrder(CurrencyMapper.class, ExchangeRateMapper.class,
                        AuditMetadataFactory.class, BaseCurrencyService.class)
                .doesNotContain(CurrencyAdminService.class, CurrencyAdminCommandService.class);
        assertThat(constructorDependencies(CurrencyAdminCommandService.class))
                .containsExactlyInAnyOrder(CurrencyMapper.class, ExchangeRateMapper.class,
                        AuditMetadataFactory.class, AccountBookCurrencyMapper.class, CurrencyAdminQueryService.class,
                        BaseCurrencyDataGuard.class)
                .doesNotContain(CurrencyAdminService.class);
    }
    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        CurrencyAdminQueryService queryService = mock(CurrencyAdminQueryService.class);
        CurrencyAdminCommandService commandService = mock(CurrencyAdminCommandService.class);
        CurrencyAdminService service = new CurrencyAdminService(queryService, commandService);

        CurrencyCreateRequest createRequest = mock(CurrencyCreateRequest.class);
        BaseCurrencyRequest baseRequest = mock(BaseCurrencyRequest.class);
        ExchangeRateRequest rateRequest = mock(ExchangeRateRequest.class);
        CurrencyResponse currencyResponse = mock(CurrencyResponse.class);
        ExchangeRateResponse rateResponse = mock(ExchangeRateResponse.class);
        List<CurrencyResponse> currencyList = List.of(currencyResponse);
        List<ExchangeRateResponse> rateList = List.of(rateResponse);

        when(queryService.baseCurrency()).thenReturn("USD");
        when(queryService.currencies()).thenReturn(currencyList);
        when(queryService.rates("USD", "CNY")).thenReturn(rateList);
        when(commandService.createCurrency(createRequest)).thenReturn(currencyResponse);
        when(commandService.enableCurrency(7L)).thenReturn(currencyResponse);
        when(commandService.disableCurrency(7L)).thenReturn(currencyResponse);
        when(commandService.setBaseCurrency(baseRequest)).thenReturn("EUR");
        when(commandService.create(rateRequest)).thenReturn(rateResponse);
        when(commandService.createRate(rateRequest)).thenReturn(rateResponse);
        when(commandService.disableRate(7L)).thenReturn(rateResponse);

        assertThat(service.baseCurrency()).isEqualTo("USD");
        assertThat(service.currencies()).isSameAs(currencyList);
        assertThat(service.rates("USD", "CNY")).isSameAs(rateList);
        assertThat(service.createCurrency(createRequest)).isSameAs(currencyResponse);
        assertThat(service.enableCurrency(7L)).isSameAs(currencyResponse);
        assertThat(service.disableCurrency(7L)).isSameAs(currencyResponse);
        assertThat(service.setBaseCurrency(baseRequest)).isEqualTo("EUR");
        assertThat(service.create(rateRequest)).isSameAs(rateResponse);
        assertThat(service.createRate(rateRequest)).isSameAs(rateResponse);
        assertThat(service.disableRate(7L)).isSameAs(rateResponse);

        verify(queryService).baseCurrency();
        verify(queryService).currencies();
        verify(queryService).rates("USD", "CNY");
        verify(commandService).createCurrency(createRequest);
        verify(commandService).enableCurrency(7L);
        verify(commandService).disableCurrency(7L);
        verify(commandService).setBaseCurrency(baseRequest);
        verify(commandService).create(rateRequest);
        verify(commandService).createRate(rateRequest);
        verify(commandService).disableRate(7L);
    }
    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{CurrencyAdminService.class, CurrencyAdminQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("baseCurrency"));
            assertReadOnly(type.getDeclaredMethod("currencies"));
            assertReadOnly(type.getDeclaredMethod("rates", String.class, String.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{CurrencyAdminService.class, CurrencyAdminCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("createCurrency", CurrencyCreateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("enableCurrency", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("disableCurrency", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("setBaseCurrency", BaseCurrencyRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("create", ExchangeRateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("disableRate", Long.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepCreateRateNonTransactional() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{CurrencyAdminService.class, CurrencyAdminCommandService.class}) {
            assertThat(type.getDeclaredMethod("createRate", ExchangeRateRequest.class)
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
