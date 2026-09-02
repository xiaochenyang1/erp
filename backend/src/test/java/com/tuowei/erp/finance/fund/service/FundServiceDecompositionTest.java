package com.tuowei.erp.finance.fund.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.fund.mapper.BankStatementMapper;
import com.tuowei.erp.finance.fund.mapper.FundAccountMapper;
import com.tuowei.erp.finance.fund.web.BankStatementCreateRequest;
import com.tuowei.erp.finance.fund.web.BankStatementMatchRequest;
import com.tuowei.erp.finance.fund.web.BankStatementPageQuery;
import com.tuowei.erp.finance.fund.web.BankStatementUnmatchRequest;
import com.tuowei.erp.finance.fund.web.FundAccountCreateRequest;
import com.tuowei.erp.finance.fund.web.FundAccountPageQuery;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FundServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(FundService.class))
                .containsExactlyInAnyOrder(FundQueryService.class, FundCommandService.class);
        assertThat(constructorDependencies(FundQueryService.class))
                .containsExactlyInAnyOrder(FundAccountMapper.class, BankStatementMapper.class, AuditMetadataFactory.class)
                .doesNotContain(FundService.class, FundCommandService.class);
        assertThat(constructorDependencies(FundCommandService.class))
                .containsExactlyInAnyOrder(FundAccountMapper.class, BankStatementMapper.class, ReceiptMapper.class,
                        PaymentMapper.class, FundStatementNumberService.class, AuditMetadataFactory.class, FundQueryService.class)
                .doesNotContain(FundService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullQueries() {
        FundQueryService query = mock(FundQueryService.class);
        FundCommandService command = mock(FundCommandService.class);
        FundService facade = new FundService(query, command);
        facade.listAccounts(null);
        facade.accountDetail(1L);
        facade.listStatements(null);
        facade.statementDetail(2L);
        facade.createAccount(mock(FundAccountCreateRequest.class));
        facade.createStatement(mock(BankStatementCreateRequest.class));
        facade.matchStatement(2L, mock(BankStatementMatchRequest.class));
        facade.unmatchStatement(2L, mock(BankStatementUnmatchRequest.class));
        verify(query).listAccounts(any(FundAccountPageQuery.class));
        verify(query).accountDetail(1L);
        verify(query).listStatements(any(BankStatementPageQuery.class));
        verify(query).statementDetail(2L);
        verify(command).createAccount(any(FundAccountCreateRequest.class));
        verify(command).createStatement(any(BankStatementCreateRequest.class));
        verify(command).matchStatement(eq(2L), any(BankStatementMatchRequest.class));
        verify(command).unmatchStatement(eq(2L), any(BankStatementUnmatchRequest.class));
    }

    @Test
    void readAndWriteTransactionBoundariesRemainExplicit() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{FundService.class, FundQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("listAccounts", FundAccountPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("accountDetail", Long.class));
            assertReadOnly(type.getDeclaredMethod("listStatements", BankStatementPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("statementDetail", Long.class));
        }
        for (Class<?> type : new Class<?>[]{FundService.class, FundCommandService.class}) {
            assertRequired(type.getDeclaredMethod("createAccount", FundAccountCreateRequest.class));
            assertRequired(type.getDeclaredMethod("createStatement", BankStatementCreateRequest.class));
            assertRequired(type.getDeclaredMethod("matchStatement", Long.class, BankStatementMatchRequest.class));
            assertRequired(type.getDeclaredMethod("unmatchStatement", Long.class, BankStatementUnmatchRequest.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
    }

    private void assertRequired(Method method) {
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isFalse();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
