package com.tuowei.erp.production.returnmaterial;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionReturnRequest;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnLineMapper;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnMapper;
import com.tuowei.erp.production.returnmaterial.service.ProductionReturnCommandService;
import com.tuowei.erp.production.returnmaterial.service.ProductionReturnService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionReturnServiceDecompositionTest {

    @Test
    void facadeKeepsProductionReturnOrchestrationBehindCommandCollaborator() {
        assertThat(autowiredDependencies(ProductionReturnService.class))
                .containsExactly(ProductionReturnCommandService.class);
        assertThat(constructorDependencies(ProductionReturnCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderService.class,
                        ProductionOrderMapper.class,
                        ProductionOrderMaterialMapper.class,
                        ProductionReturnMapper.class,
                        ProductionReturnLineMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        AccountPeriodGuard.class,
                        FinancePostingService.class,
                        AuditMetadataFactory.class,
                        SequenceNumberGenerator.class
                )
                .doesNotContain(ProductionReturnService.class);
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransaction() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ProductionReturnService.class.getDeclaredMethod(
                "returnMaterials", Long.class, ProductionReturnRequest.class));
        assertRequiredWriteTransaction(ProductionReturnCommandService.class.getDeclaredMethod(
                "returnMaterials", Long.class, ProductionReturnRequest.class));
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

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
