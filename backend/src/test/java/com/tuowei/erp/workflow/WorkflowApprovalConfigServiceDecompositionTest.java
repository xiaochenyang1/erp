package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalConfigMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeApproverMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeMapper;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigQueryService;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApprovalConfigServiceDecompositionTest {

    @Test
    void facadeDelegatesReadSideDependenciesToQueryService() {
        assertThat(constructorDependencies(WorkflowApprovalConfigService.class))
                .hasSize(5)
                .containsExactlyInAnyOrder(
                        WorkflowApprovalConfigMapper.class,
                        WorkflowApprovalNodeMapper.class,
                        WorkflowApprovalNodeApproverMapper.class,
                        AuditMetadataFactory.class,
                        WorkflowApprovalConfigQueryService.class
                )
                .doesNotContain(
                        UserMapper.class,
                        UserRoleMapper.class,
                        RoleMapper.class,
                        RoleMenuMapper.class,
                        MenuMapper.class
                );
        assertThat(constructorDependencies(WorkflowApprovalConfigQueryService.class))
                .hasSize(9)
                .contains(
                        WorkflowApprovalConfigMapper.class,
                        WorkflowApprovalNodeMapper.class,
                        WorkflowApprovalNodeApproverMapper.class,
                        AuditMetadataFactory.class,
                        UserMapper.class,
                        UserRoleMapper.class,
                        RoleMapper.class,
                        RoleMenuMapper.class,
                        MenuMapper.class
                )
                .doesNotContain(WorkflowApprovalConfigService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyConfigLookup() throws NoSuchMethodException {
        assertReadOnly(WorkflowApprovalConfigService.class.getDeclaredMethod(
                "getByBusinessType", String.class));
        assertReadOnly(WorkflowApprovalConfigQueryService.class.getDeclaredMethod(
                "getByBusinessType", String.class));
    }

    @Test
    void facadeKeepsRequiredWriteTransactionOnSave() throws NoSuchMethodException {
        Method save = WorkflowApprovalConfigService.class.getDeclaredMethod(
                "save", String.class, WorkflowApprovalConfigRequest.class);
        Transactional transactional = save.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
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
}
