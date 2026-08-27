package com.tuowei.erp.finance;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.service.PaymentCommandService;
import com.tuowei.erp.finance.payment.service.PaymentNumberService;
import com.tuowei.erp.finance.payment.service.PaymentQueryService;
import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentServiceDecompositionTest {
    @Test void dependenciesAreOneWay() {
        assertThat(autowired(PaymentService.class)).containsExactlyInAnyOrder(PaymentQueryService.class, PaymentCommandService.class);
        assertThat(deps(PaymentQueryService.class)).containsExactlyInAnyOrder(PaymentMapper.class, PaymentAllocationMapper.class, AuditMetadataFactory.class).doesNotContain(PaymentService.class, PaymentCommandService.class);
        assertThat(deps(PaymentCommandService.class)).containsExactlyInAnyOrder(PaymentMapper.class, PaymentAllocationMapper.class, PayableMapper.class, PaymentNumberService.class, AuditMetadataFactory.class, AccountPeriodGuard.class, PaymentQueryService.class).doesNotContain(PaymentService.class);
    }
    @Test void facadeDelegates() {
        PaymentQueryService query = mock(PaymentQueryService.class); PaymentCommandService command = mock(PaymentCommandService.class); PaymentService service = new PaymentService(query, command); PaymentCreateRequest create = null; PaymentCancelRequest cancel = null;
        service.create(create); service.list(null); service.detail(1L); service.cancel(1L, cancel);
        verify(command).create(create); verify(query).list(any(PaymentPageQuery.class)); verify(query).detail(1L); verify(command).cancel(1L, cancel);
    }
    @Test void transactionsRemainStable() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{PaymentService.class, PaymentQueryService.class}) { readOnly(type.getDeclaredMethod("list", PaymentPageQuery.class)); readOnly(type.getDeclaredMethod("detail", Long.class)); }
        for (Class<?> type : new Class<?>[]{PaymentService.class, PaymentCommandService.class}) { required(type.getDeclaredMethod("create", PaymentCreateRequest.class)); required(type.getDeclaredMethod("cancel", Long.class, PaymentCancelRequest.class)); }
    }
    private Set<Class<?>> deps(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private Set<Class<?>> autowired(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Autowired.class)).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private void readOnly(Method m) { Transactional tx = m.getAnnotation(Transactional.class); assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isTrue(); }
    private void required(Method m) { Transactional tx = m.getAnnotation(Transactional.class); assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isFalse(); assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED); }
}
