package com.tuowei.erp.masterdata.product;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductCommandService;
import com.tuowei.erp.masterdata.product.service.ProductQueryService;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import com.tuowei.erp.system.dict.service.SystemDictService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(ProductService.class))
                .containsExactlyInAnyOrder(ProductQueryService.class, ProductCommandService.class);
        assertThat(constructorDependencies(ProductQueryService.class))
                .containsExactlyInAnyOrder(ProductMapper.class, AuditMetadataFactory.class)
                .doesNotContain(ProductService.class, ProductCommandService.class);
        assertThat(constructorDependencies(ProductCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductMapper.class,
                        InventoryBalanceMapper.class,
                        InventoryLotBalanceMapper.class,
                        AuditMetadataFactory.class,
                        SystemDictService.class,
                        ProductQueryService.class
                )
                .doesNotContain(ProductService.class);
    }

    @Test
    void facadeDelegatesPublicApiAndNormalizesNullListQuery() {
        ProductQueryService queryService = mock(ProductQueryService.class);
        ProductCommandService commandService = mock(ProductCommandService.class);
        ProductService service = new ProductService(queryService, commandService);
        ProductCreateRequest createRequest = createRequest();
        ProductUpdateRequest updateRequest = updateRequest();
        StreamingResponseBody export = outputStream -> { };
        when(queryService.exportProducts(any(ProductPageQuery.class))).thenReturn(export);

        service.create(createRequest);
        service.getById(10L);
        service.getByBarcode("BAR-10");
        service.list(null);
        service.exportProducts(new ProductPageQuery());
        service.update(10L, updateRequest);
        service.enable(10L);
        service.disable(10L);

        verify(commandService).create(createRequest);
        verify(queryService).getById(10L);
        verify(queryService).getByBarcode("BAR-10");
        verify(queryService).list(any(ProductPageQuery.class));
        verify(queryService).exportProducts(any(ProductPageQuery.class));
        verify(commandService).update(10L, updateRequest);
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionsWhileExportOwnsNoTransaction()
            throws NoSuchMethodException {
        Class<?>[] queryServices = {ProductService.class, ProductQueryService.class};
        for (Class<?> serviceType : queryServices) {
            assertReadOnly(serviceType.getDeclaredMethod("getById", Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("getByBarcode", String.class));
            assertReadOnly(serviceType.getDeclaredMethod("list", ProductPageQuery.class));
            assertThat(serviceType.getDeclaredMethod("exportProducts", ProductPageQuery.class)
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] commandServices = {ProductService.class, ProductCommandService.class};
        for (Class<?> serviceType : commandServices) {
            assertRequiredWrite(serviceType.getDeclaredMethod("create", ProductCreateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("update", Long.class, ProductUpdateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("disable", Long.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private ProductCreateRequest createRequest() {
        return new ProductCreateRequest(
                "P-10", "商品", "STANDARD", "分类", null, "个",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, null, "BAR-10"
        );
    }

    private ProductUpdateRequest updateRequest() {
        return new ProductUpdateRequest(
                "商品更新", "分类", null, "个",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, null, "BAR-10"
        );
    }
}
