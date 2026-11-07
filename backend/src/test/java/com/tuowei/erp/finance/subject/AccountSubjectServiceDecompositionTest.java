package com.tuowei.erp.finance.subject;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectCommandService;
import com.tuowei.erp.finance.subject.service.AccountSubjectQueryService;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectTreeNode;
import com.tuowei.erp.finance.subject.web.AccountSubjectUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSubjectServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(AccountSubjectService.class))
                .containsExactlyInAnyOrder(AccountSubjectQueryService.class, AccountSubjectCommandService.class);
        assertThat(constructorDependencies(AccountSubjectQueryService.class))
                .containsExactlyInAnyOrder(AccountSubjectMapper.class, AuditMetadataFactory.class)
                .doesNotContain(AccountSubjectService.class, AccountSubjectCommandService.class);
        assertThat(constructorDependencies(AccountSubjectCommandService.class))
                .containsExactlyInAnyOrder(
                        AccountSubjectMapper.class,
                        AuditMetadataFactory.class,
                        AccountSubjectQueryService.class
                )
                .doesNotContain(AccountSubjectService.class);
    }
    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        AccountSubjectQueryService queryService = mock(AccountSubjectQueryService.class);
        AccountSubjectCommandService commandService = mock(AccountSubjectCommandService.class);
        AccountSubjectService service = new AccountSubjectService(queryService, commandService);

        AccountSubjectPageQuery query = new AccountSubjectPageQuery();
        AccountSubjectCreateRequest createRequest = mock(AccountSubjectCreateRequest.class);
        AccountSubjectUpdateRequest updateRequest = mock(AccountSubjectUpdateRequest.class);
        PageResponse<AccountSubjectResponse> pageResponse = mock(PageResponse.class);
        AccountSubjectResponse response = mock(AccountSubjectResponse.class);
        List<AccountSubjectTreeNode> treeNodes = List.of(mock(AccountSubjectTreeNode.class));
        AccountSubjectEntity entity = mock(AccountSubjectEntity.class);

        when(commandService.create(createRequest)).thenReturn(response);
        when(queryService.list(query)).thenReturn(pageResponse);
        when(queryService.detail(7L)).thenReturn(response);
        when(queryService.tree()).thenReturn(treeNodes);
        when(commandService.update(7L, updateRequest)).thenReturn(response);
        when(commandService.enable(7L)).thenReturn(response);
        when(commandService.disable(7L)).thenReturn(response);
        when(queryService.requireActiveSubject(7L, "msg")).thenReturn(entity);

        assertThat(service.create(createRequest)).isSameAs(response);
        assertThat(service.list(query)).isSameAs(pageResponse);
        assertThat(service.detail(7L)).isSameAs(response);
        assertThat(service.tree()).isSameAs(treeNodes);
        assertThat(service.update(7L, updateRequest)).isSameAs(response);
        assertThat(service.enable(7L)).isSameAs(response);
        assertThat(service.disable(7L)).isSameAs(response);
        assertThat(service.requireActiveSubject(7L, "msg")).isSameAs(entity);

        verify(commandService).create(createRequest);
        verify(queryService).list(query);
        verify(queryService).detail(7L);
        verify(queryService).tree();
        verify(commandService).update(7L, updateRequest);
        verify(commandService).enable(7L);
        verify(commandService).disable(7L);
        verify(queryService).requireActiveSubject(7L, "msg");
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{AccountSubjectService.class, AccountSubjectQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", AccountSubjectPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("detail", Long.class));
            assertReadOnly(type.getDeclaredMethod("tree"));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{AccountSubjectService.class, AccountSubjectCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("create", AccountSubjectCreateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("update", Long.class, AccountSubjectUpdateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("disable", Long.class));
        }
    }

    @Test
    void facadeAndQueryServiceKeepRequireActiveSubjectNonTransactional() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{AccountSubjectService.class, AccountSubjectQueryService.class}) {
            Method method = type.getDeclaredMethod("requireActiveSubject", Long.class, String.class);
            assertThat(method.getAnnotation(Transactional.class)).isNull();
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

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
