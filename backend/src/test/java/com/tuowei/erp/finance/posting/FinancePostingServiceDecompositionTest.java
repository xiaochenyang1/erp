package com.tuowei.erp.finance.posting;

import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FinancePostingServiceDecompositionTest {

    @Test
    void financePostingFacadeKeepsSubledgerAndVoucherPersistenceBehindDedicatedServices() {
        assertThat(constructorDependencies(FinancePostingService.class))
                .contains(FinanceSubledgerPostingService.class, FinanceVoucherPostingService.class)
                .doesNotContain(
                        PayableMapper.class,
                        ReceivableMapper.class,
                        CustomerMapper.class,
                        SupplierMapper.class,
                        VoucherMapper.class,
                        VoucherEntryMapper.class,
                        AccountSubjectMapper.class
                );
        assertThat(constructorDependencies(FinanceSubledgerPostingService.class))
                .contains(
                        PayableMapper.class,
                        ReceivableMapper.class,
                        CustomerMapper.class,
                        SupplierMapper.class
                );
        assertThat(autowiredConstructorDependencies(FinanceVoucherPostingService.class))
                .contains(FinanceVoucherPersistenceService.class)
                .doesNotContain(VoucherMapper.class, VoucherEntryMapper.class, AccountSubjectMapper.class);
        assertThat(constructorDependencies(FinanceVoucherPersistenceService.class))
                .contains(VoucherMapper.class, VoucherEntryMapper.class, AccountSubjectMapper.class)
                .doesNotContain(FinanceVoucherPostingService.class);
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

    @Test
    void facadeAndVoucherWritesUseRequiredTransactions() {
        assertThat(Arrays.stream(FinancePostingService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(this::assertRequiredWriteTransaction);
        assertThat(Arrays.stream(FinanceVoucherPostingService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(this::assertRequiredWriteTransaction);
        assertThat(Arrays.stream(FinanceVoucherPersistenceService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class)).isNull());
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
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
