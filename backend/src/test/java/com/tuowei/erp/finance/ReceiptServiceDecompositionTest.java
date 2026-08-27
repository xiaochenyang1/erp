package com.tuowei.erp.finance;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.service.ReceiptCommandService;
import com.tuowei.erp.finance.receipt.service.ReceiptNumberService;
import com.tuowei.erp.finance.receipt.service.ReceiptQueryService;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
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

class ReceiptServiceDecompositionTest {
    @Test void dependenciesAreOneWay() {
        assertThat(autowired(ReceiptService.class)).containsExactlyInAnyOrder(ReceiptQueryService.class, ReceiptCommandService.class);
        assertThat(deps(ReceiptQueryService.class)).containsExactlyInAnyOrder(ReceiptMapper.class, ReceiptAllocationMapper.class, AuditMetadataFactory.class).doesNotContain(ReceiptService.class, ReceiptCommandService.class);
        assertThat(deps(ReceiptCommandService.class)).containsExactlyInAnyOrder(ReceiptMapper.class, ReceiptAllocationMapper.class, ReceivableMapper.class, ReceiptNumberService.class, AuditMetadataFactory.class, AccountPeriodGuard.class, ReceiptQueryService.class).doesNotContain(ReceiptService.class);
    }
    @Test void facadeDelegates() {
        ReceiptQueryService query = mock(ReceiptQueryService.class); ReceiptCommandService command = mock(ReceiptCommandService.class); ReceiptService service = new ReceiptService(query, command); ReceiptCreateRequest create = null; ReceiptCancelRequest cancel = null;
        service.create(create); service.list(null); service.detail(1L); service.cancel(1L, cancel);
        verify(command).create(create); verify(query).list(any(ReceiptPageQuery.class)); verify(query).detail(1L); verify(command).cancel(1L, cancel);
    }
    @Test void transactionsRemainStable() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ReceiptService.class, ReceiptQueryService.class}) { readOnly(type.getDeclaredMethod("list", ReceiptPageQuery.class)); readOnly(type.getDeclaredMethod("detail", Long.class)); }
        for (Class<?> type : new Class<?>[]{ReceiptService.class, ReceiptCommandService.class}) { required(type.getDeclaredMethod("create", ReceiptCreateRequest.class)); required(type.getDeclaredMethod("cancel", Long.class, ReceiptCancelRequest.class)); }
    }
    private Set<Class<?>> deps(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private Set<Class<?>> autowired(Class<?> type) { return Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Autowired.class)).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet()); }
    private void readOnly(Method m) { Transactional tx = m.getAnnotation(Transactional.class); assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isTrue(); }
    private void required(Method m) { Transactional tx = m.getAnnotation(Transactional.class); assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isFalse(); assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED); }
}
