package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyAssemblyService;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyQueryService;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyService;
import com.tuowei.erp.inventory.stock.service.LotGenealogyCounterpartyResolver;
import com.tuowei.erp.inventory.stock.service.LotGenealogyDisplayResolver;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryLotGenealogyServiceDecompositionTest {

    @Test
    void facadeUsesQueryCollaboratorAndQueryUsesAssemblyCollaborator() {
        assertThat(autowiredDependencies(InventoryLotGenealogyService.class))
                .containsExactly(InventoryLotGenealogyQueryService.class);
        assertThat(constructorDependencies(InventoryLotGenealogyQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryTransactionMapper.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        InventoryLotGenealogyAssemblyService.class
                )
                .doesNotContain(InventoryLotGenealogyService.class);
        assertThat(constructorDependencies(InventoryLotGenealogyAssemblyService.class))
                .containsExactlyInAnyOrder(
                        InventoryDocumentLinkResolver.class,
                        LotGenealogyCounterpartyResolver.class,
                        LotGenealogyDisplayResolver.class
                )
                .doesNotContain(InventoryLotGenealogyQueryService.class);
    }

    @Test
    void facadeDelegatesGenealogyWithoutChangingQueryObject() {
        InventoryLotGenealogyQueryService queryService = mock(InventoryLotGenealogyQueryService.class);
        InventoryLotGenealogyService facade = new InventoryLotGenealogyService(queryService);
        InventoryLotGenealogyQuery query = new InventoryLotGenealogyQuery();

        facade.genealogy(query);

        verify(queryService).genealogy(query);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactionContract() throws NoSuchMethodException {
        assertReadOnly(InventoryLotGenealogyService.class.getDeclaredMethod(
                "genealogy", InventoryLotGenealogyQuery.class));
        assertReadOnly(InventoryLotGenealogyQueryService.class.getDeclaredMethod(
                "genealogy", InventoryLotGenealogyQuery.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
