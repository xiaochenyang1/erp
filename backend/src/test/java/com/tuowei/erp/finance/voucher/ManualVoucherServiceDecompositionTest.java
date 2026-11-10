package com.tuowei.erp.finance.voucher;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.service.ManualVoucherCommandService;
import com.tuowei.erp.finance.voucher.service.ManualVoucherPostingService;
import com.tuowei.erp.finance.voucher.service.ManualVoucherQueryService;
import com.tuowei.erp.finance.voucher.service.ManualVoucherService;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ManualVoucherServiceDecompositionTest {

    @Test
    void facadeKeepsAllOrchestrationBehindDedicatedCollaborators() {
        assertThat(constructorDependencies(ManualVoucherService.class))
                .containsExactlyInAnyOrder(
                        ManualVoucherQueryService.class,
                        ManualVoucherCommandService.class,
                        ManualVoucherPostingService.class
                );
        assertThat(constructorDependencies(ManualVoucherQueryService.class))
                .containsExactlyInAnyOrder(
                        ManualVoucherMapper.class,
                        ManualVoucherLineMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(ManualVoucherService.class, ManualVoucherCommandService.class);
        assertThat(constructorDependencies(ManualVoucherCommandService.class))
                .containsExactlyInAnyOrder(
                        ManualVoucherMapper.class,
                        ManualVoucherLineMapper.class,
                        AccountSubjectMapper.class,
                        SequenceNumberGenerator.class,
                        AuditMetadataFactory.class,
                        ManualVoucherQueryService.class,
                        AttachmentService.class
                )
                .doesNotContain(ManualVoucherService.class, ManualVoucherPostingService.class);
        assertThat(constructorDependencies(ManualVoucherPostingService.class))
                .containsExactlyInAnyOrder(
                        ManualVoucherMapper.class,
                        VoucherMapper.class,
                        VoucherEntryMapper.class,
                        AccountPeriodGuard.class,
                        AuditMetadataFactory.class,
                        ManualVoucherQueryService.class,
                        AttachmentService.class
                )
                .doesNotContain(
                        ManualVoucherService.class,
                        ManualVoucherCommandService.class,
                        ManualVoucherLineMapper.class,
                        AccountSubjectMapper.class,
                        SequenceNumberGenerator.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ManualVoucherService.class.getDeclaredMethod("list", ManualVoucherPageQuery.class));
        assertReadOnly(ManualVoucherService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod("list", ManualVoucherPageQuery.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod(
                "requireVoucher",
                Long.class,
                AuditMetadata.class
        ));
    }

    @Test
    void allFacadeAndCommandWriteMethodsKeepRequiredTransactions() throws NoSuchMethodException {
        Class<?>[] writeServices = {
                ManualVoucherService.class,
                ManualVoucherCommandService.class
        };
        for (Class<?> serviceType : writeServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "create",
                    com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "update",
                    Long.class,
                    com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("submit", Long.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("approve", Long.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "reject",
                    Long.class,
                    String.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("delete", Long.class));
        }
        assertRequiredWriteTransaction(ManualVoucherService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ManualVoucherService.class.getDeclaredMethod(
                "cancel",
                Long.class,
                String.class
        ));
        assertRequiredWriteTransaction(ManualVoucherPostingService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ManualVoucherPostingService.class.getDeclaredMethod(
                "cancel",
                Long.class,
                String.class
        ));
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
