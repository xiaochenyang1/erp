package com.tuowei.erp.production.bom;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.service.ProductionBomCommandService;
import com.tuowei.erp.production.bom.service.ProductionBomNumberService;
import com.tuowei.erp.production.bom.service.ProductionBomQueryService;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionBomServiceDecompositionTest {
    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(autowiredDependencies(ProductionBomService.class))
                .containsExactlyInAnyOrder(ProductionBomQueryService.class, ProductionBomCommandService.class);
        assertThat(constructorDependencies(ProductionBomQueryService.class))
                .containsExactlyInAnyOrder(ProductionBomMapper.class, ProductionBomLineMapper.class, AuditMetadataFactory.class)
                .doesNotContain(ProductionBomService.class, ProductionBomCommandService.class);
        assertThat(constructorDependencies(ProductionBomCommandService.class))
                .containsExactlyInAnyOrder(ProductionBomMapper.class, ProductionBomLineMapper.class,
                        ProductionBomNumberService.class, ProductValidator.class, AuditMetadataFactory.class,
                        ProductionBomQueryService.class)
                .doesNotContain(ProductionBomService.class);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionSemantics() throws NoSuchMethodException {
        assertReadOnly(ProductionBomService.class.getDeclaredMethod("list", ProductionBomPageQuery.class));
        assertReadOnly(ProductionBomService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(ProductionBomQueryService.class.getDeclaredMethod("list", ProductionBomPageQuery.class));
        assertReadOnly(ProductionBomQueryService.class.getDeclaredMethod("getById", Long.class));
        assertRequiredWrite(ProductionBomService.class.getDeclaredMethod("create", ProductionBomCreateRequest.class));
        assertRequiredWrite(ProductionBomService.class.getDeclaredMethod("update", Long.class, ProductionBomUpdateRequest.class));
        assertRequiredWrite(ProductionBomCommandService.class.getDeclaredMethod("create", ProductionBomCreateRequest.class));
        assertRequiredWrite(ProductionBomCommandService.class.getDeclaredMethod("update", Long.class, ProductionBomUpdateRequest.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private Set<Class<?>> autowiredDependencies(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Autowired.class)).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private void assertReadOnly(Method method) { Transactional t = method.getAnnotation(Transactional.class); assertThat(t).isNotNull(); assertThat(t.readOnly()).isTrue(); }
    private void assertRequiredWrite(Method method) { Transactional t = method.getAnnotation(Transactional.class); assertThat(t).isNotNull(); assertThat(t.readOnly()).isFalse(); assertThat(t.propagation()).isEqualTo(Propagation.REQUIRED); }
}
