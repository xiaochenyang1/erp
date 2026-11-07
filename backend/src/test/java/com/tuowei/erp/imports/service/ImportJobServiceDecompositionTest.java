package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.config.ImportProperties;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.mapper.ImportJobRowMapper;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.imports.web.ImportJobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(ImportJobService.class))
                .containsExactlyInAnyOrder(ImportJobQueryService.class, ImportJobCommandService.class);
        assertThat(constructorDependencies(ImportJobQueryService.class))
                .containsExactlyInAnyOrder(
                        ImportJobMapper.class,
                        ImportJobRowMapper.class,
                        ImportTemplateRegistry.class,
                        AuditMetadataFactory.class,
                        ImportValidationSupport.class
                )
                .doesNotContain(ImportJobService.class, ImportJobCommandService.class);
        assertThat(constructorDependencies(ImportJobCommandService.class))
                .containsExactlyInAnyOrder(
                        ImportJobMapper.class,
                        ImportJobRowMapper.class,
                        ImportTemplateRegistry.class,
                        CsvImportParser.class,
                        AuditMetadataFactory.class,
                        PlatformTransactionManager.class,
                        List.class,
                        ImportValidationSupport.class,
                        AccountPeriodGuard.class,
                        ImportProperties.class,
                        ImportJobQueryService.class
                )
                .doesNotContain(ImportJobService.class);
    }

    @Test
    void facadeDelegatesPublicApiAndNormalizesNullListQuery() {
        ImportJobQueryService queryService = mock(ImportJobQueryService.class);
        ImportJobCommandService commandService = mock(ImportJobCommandService.class);
        ImportJobService service = new ImportJobService(queryService, commandService);
        MultipartFile file = mock(MultipartFile.class);
        ImportJobResponse response = mock(ImportJobResponse.class);
        ResponseEntity<ByteArrayResource> csv = ResponseEntity.ok(new ByteArrayResource(new byte[0]));
        when(queryService.template("PRODUCT")).thenReturn(csv);
        when(queryService.detail(88L)).thenReturn(response);
        when(queryService.exportErrorRows(88L)).thenReturn(csv);
        when(commandService.preview("PRODUCT", file)).thenReturn(response);
        when(commandService.commit(88L)).thenReturn(response);

        service.template("PRODUCT");
        service.list(null);
        service.detail(88L);
        service.exportErrorRows(88L);
        service.preview("PRODUCT", file);
        service.commit(88L);

        verify(queryService).template("PRODUCT");
        verify(queryService).list(any(ImportJobPageQuery.class));
        verify(queryService).detail(88L);
        verify(queryService).exportErrorRows(88L);
        verify(commandService).preview("PRODUCT", file);
        verify(commandService).commit(88L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionBoundaries() throws NoSuchMethodException {
        Class<?>[] queryServices = {ImportJobService.class, ImportJobQueryService.class};
        for (Class<?> serviceType : queryServices) {
            assertReadOnly(serviceType.getDeclaredMethod("list", ImportJobPageQuery.class));
            assertReadOnly(serviceType.getDeclaredMethod("detail", Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("exportErrorRows", Long.class));
        }
    }

    @Test
    void previewKeepsRequiredWriteTransactionAndCommitKeepsExplicitTransactionOwnership()
            throws NoSuchMethodException {
        assertRequiredWriteTransaction(ImportJobService.class.getDeclaredMethod(
                "preview",
                String.class,
                MultipartFile.class
        ));
        assertRequiredWriteTransaction(ImportJobCommandService.class.getDeclaredMethod(
                "preview",
                String.class,
                MultipartFile.class
        ));
        assertThat(ImportJobService.class.getDeclaredMethod("commit", Long.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(ImportJobCommandService.class.getDeclaredMethod("commit", Long.class)
                .getAnnotation(Transactional.class)).isNull();
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
