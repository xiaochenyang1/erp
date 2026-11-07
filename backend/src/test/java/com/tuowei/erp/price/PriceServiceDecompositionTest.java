package com.tuowei.erp.price;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.price.mapper.PurchasePriceMapper;
import com.tuowei.erp.purchase.price.service.PurchasePriceCommandService;
import com.tuowei.erp.purchase.price.service.PurchasePriceQueryService;
import com.tuowei.erp.purchase.price.service.PurchasePriceService;
import com.tuowei.erp.purchase.price.web.PurchasePriceCreateRequest;
import com.tuowei.erp.purchase.price.web.PurchasePricePageQuery;
import com.tuowei.erp.purchase.price.web.PurchasePriceUpdateRequest;
import com.tuowei.erp.sales.price.mapper.SalesPriceMapper;
import com.tuowei.erp.sales.price.service.SalesPriceCommandService;
import com.tuowei.erp.sales.price.service.SalesPriceQueryService;
import com.tuowei.erp.sales.price.service.SalesPriceService;
import com.tuowei.erp.sales.price.web.SalesPriceCreateRequest;
import com.tuowei.erp.sales.price.web.SalesPricePageQuery;
import com.tuowei.erp.sales.price.web.SalesPriceUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PriceServiceDecompositionTest {

    @Test
    void facadesDependOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(SalesPriceService.class))
                .containsExactlyInAnyOrder(SalesPriceQueryService.class, SalesPriceCommandService.class);
        assertThat(constructorDependencies(SalesPriceQueryService.class))
                .containsExactlyInAnyOrder(
                        SalesPriceMapper.class,
                        ProductMapper.class,
                        CustomerMapper.class,
                        ProductValidator.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(SalesPriceService.class, SalesPriceCommandService.class);
        assertThat(constructorDependencies(SalesPriceCommandService.class))
                .containsExactlyInAnyOrder(
                        SalesPriceMapper.class,
                        CustomerMapper.class,
                        ProductValidator.class,
                        AuditMetadataFactory.class,
                        SalesPriceQueryService.class
                )
                .doesNotContain(SalesPriceService.class);

        assertThat(constructorDependencies(PurchasePriceService.class))
                .containsExactlyInAnyOrder(PurchasePriceQueryService.class, PurchasePriceCommandService.class);
        assertThat(constructorDependencies(PurchasePriceQueryService.class))
                .containsExactlyInAnyOrder(
                        PurchasePriceMapper.class,
                        ProductMapper.class,
                        SupplierMapper.class,
                        ProductValidator.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(PurchasePriceService.class, PurchasePriceCommandService.class);
        assertThat(constructorDependencies(PurchasePriceCommandService.class))
                .containsExactlyInAnyOrder(
                        PurchasePriceMapper.class,
                        SupplierMapper.class,
                        ProductValidator.class,
                        AuditMetadataFactory.class,
                        PurchasePriceQueryService.class
                )
                .doesNotContain(PurchasePriceService.class);
    }

    @Test
    void salesFacadeDelegatesAllPublicApisAndNormalizesNullListQuery() {
        SalesPriceQueryService queryService = mock(SalesPriceQueryService.class);
        SalesPriceCommandService commandService = mock(SalesPriceCommandService.class);
        SalesPriceService service = new SalesPriceService(queryService, commandService);
        SalesPriceCreateRequest createRequest = new SalesPriceCreateRequest(
                10L, 20L, BigDecimal.TEN, BigDecimal.ONE, LocalDate.of(2026, 8, 1), null, null
        );
        SalesPriceUpdateRequest updateRequest = new SalesPriceUpdateRequest(
                10L, 20L, BigDecimal.TEN, BigDecimal.ONE, LocalDate.of(2026, 8, 1), null, "ACTIVE", null
        );
        LocalDate date = LocalDate.of(2026, 8, 22);

        service.create(createRequest);
        service.update(1L, updateRequest);
        service.enable(1L);
        service.disable(1L);
        service.getById(1L);
        service.list(null);
        service.resolve(10L, 20L, date);
        service.resolveMinPrice(1L, 2L, 10L, 20L, date);

        verify(commandService).create(createRequest);
        verify(commandService).update(1L, updateRequest);
        verify(commandService).enable(1L);
        verify(commandService).disable(1L);
        verify(queryService).getById(1L);
        verify(queryService).list(any(SalesPricePageQuery.class));
        verify(queryService).resolve(10L, 20L, date);
        verify(queryService).resolveMinPrice(1L, 2L, 10L, 20L, date);
    }

    @Test
    void purchaseFacadeDelegatesAllPublicApisAndNormalizesNullListQuery() {
        PurchasePriceQueryService queryService = mock(PurchasePriceQueryService.class);
        PurchasePriceCommandService commandService = mock(PurchasePriceCommandService.class);
        PurchasePriceService service = new PurchasePriceService(queryService, commandService);
        PurchasePriceCreateRequest createRequest = new PurchasePriceCreateRequest(
                10L, 20L, BigDecimal.ONE, BigDecimal.TEN, LocalDate.of(2026, 8, 1), null, null
        );
        PurchasePriceUpdateRequest updateRequest = new PurchasePriceUpdateRequest(
                10L, 20L, BigDecimal.ONE, BigDecimal.TEN, LocalDate.of(2026, 8, 1), null, "ACTIVE", null
        );
        LocalDate date = LocalDate.of(2026, 8, 22);

        service.create(createRequest);
        service.update(1L, updateRequest);
        service.enable(1L);
        service.disable(1L);
        service.getById(1L);
        service.list(null);
        service.resolve(10L, 20L, date);
        service.resolveMaxPrice(1L, 2L, 10L, 20L, date);

        verify(commandService).create(createRequest);
        verify(commandService).update(1L, updateRequest);
        verify(commandService).enable(1L);
        verify(commandService).disable(1L);
        verify(queryService).getById(1L);
        verify(queryService).list(any(PurchasePricePageQuery.class));
        verify(queryService).resolve(10L, 20L, date);
        verify(queryService).resolveMaxPrice(1L, 2L, 10L, 20L, date);
    }

    @Test
    void facadesAndQueryCollaboratorsKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertSalesQueryTransactions(SalesPriceService.class);
        assertSalesQueryTransactions(SalesPriceQueryService.class);
        assertPurchaseQueryTransactions(PurchasePriceService.class);
        assertPurchaseQueryTransactions(PurchasePriceQueryService.class);
    }

    @Test
    void facadesAndCommandCollaboratorsKeepRequiredWriteTransactions() throws NoSuchMethodException {
        assertSalesCommandTransactions(SalesPriceService.class);
        assertSalesCommandTransactions(SalesPriceCommandService.class);
        assertPurchaseCommandTransactions(PurchasePriceService.class);
        assertPurchaseCommandTransactions(PurchasePriceCommandService.class);
    }

    private void assertSalesQueryTransactions(Class<?> type) throws NoSuchMethodException {
        assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        assertReadOnly(type.getDeclaredMethod("list", SalesPricePageQuery.class));
        assertReadOnly(type.getDeclaredMethod("resolve", Long.class, Long.class, LocalDate.class));
        assertReadOnly(type.getDeclaredMethod(
                "resolveMinPrice", Long.class, Long.class, Long.class, Long.class, LocalDate.class
        ));
    }

    private void assertPurchaseQueryTransactions(Class<?> type) throws NoSuchMethodException {
        assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        assertReadOnly(type.getDeclaredMethod("list", PurchasePricePageQuery.class));
        assertReadOnly(type.getDeclaredMethod("resolve", Long.class, Long.class, LocalDate.class));
        assertReadOnly(type.getDeclaredMethod(
                "resolveMaxPrice", Long.class, Long.class, Long.class, Long.class, LocalDate.class
        ));
    }

    private void assertSalesCommandTransactions(Class<?> type) throws NoSuchMethodException {
        assertRequiredWrite(type.getDeclaredMethod("create", SalesPriceCreateRequest.class));
        assertRequiredWrite(type.getDeclaredMethod("update", Long.class, SalesPriceUpdateRequest.class));
        assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
        assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
    }

    private void assertPurchaseCommandTransactions(Class<?> type) throws NoSuchMethodException {
        assertRequiredWrite(type.getDeclaredMethod("create", PurchasePriceCreateRequest.class));
        assertRequiredWrite(type.getDeclaredMethod("update", Long.class, PurchasePriceUpdateRequest.class));
        assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
        assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
