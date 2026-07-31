package com.tuowei.erp.finance.posting;

import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FinancePostingServiceDecompositionTest {

    @Test
    void financePostingFacadeKeepsSubledgerPersistenceBehindDedicatedService() {
        assertThat(constructorDependencies(FinancePostingService.class))
                .contains(FinanceSubledgerPostingService.class)
                .doesNotContain(
                        PayableMapper.class,
                        ReceivableMapper.class,
                        CustomerMapper.class,
                        SupplierMapper.class
                );
        assertThat(constructorDependencies(FinanceSubledgerPostingService.class))
                .contains(
                        PayableMapper.class,
                        ReceivableMapper.class,
                        CustomerMapper.class,
                        SupplierMapper.class
                );
    }

    @Test
    void subledgerWritesJoinTheFacadeTransaction() throws NoSuchMethodException {
        assertRequiredWriteTransaction(
                FinanceSubledgerPostingService.class.getDeclaredMethod(
                        "recordPayableIfAbsent",
                        String.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        java.time.LocalDate.class,
                        java.math.BigDecimal.class,
                        String.class,
                        com.tuowei.erp.common.security.AuditMetadata.class
                )
        );
        assertRequiredWriteTransaction(
                FinanceSubledgerPostingService.class.getDeclaredMethod(
                        "recordReceivableIfAbsent",
                        String.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        java.time.LocalDate.class,
                        java.math.BigDecimal.class,
                        String.class,
                        com.tuowei.erp.common.security.AuditMetadata.class
                )
        );
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
