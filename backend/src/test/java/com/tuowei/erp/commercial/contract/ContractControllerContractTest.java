package com.tuowei.erp.commercial.contract;

import com.tuowei.erp.commercial.contract.controller.ContractController;
import com.tuowei.erp.common.security.PermissionCodes;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ContractControllerContractTest {

    @Test
    void exposesViewManageAndApproveBoundaries() throws Exception {
        assertThat(permission("list", com.tuowei.erp.commercial.contract.web.ContractPageQuery.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_VIEW);
        assertThat(permission("create", com.tuowei.erp.commercial.contract.web.ContractSaveRequest.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_MANAGE);
        assertThat(permission("approve", Long.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_APPROVE);
        assertThat(permission("reject", Long.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_APPROVE);
        assertThat(permission("close", Long.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_APPROVE);
        assertThat(permission("cancel", Long.class)).isEqualTo(PermissionCodes.HAS_CONTRACT_MANAGE);
        assertThat(PermissionCodes.allPermissions()).contains("contract:view", "contract:manage", "contract:approve");
    }

    @Test
    void keepsLifecycleRoutesExplicit() throws Exception {
        assertThat(ContractController.class.getDeclaredMethod("approve", Long.class).getAnnotation(PostMapping.class).value())
                .containsExactly("/{id}/approve");
        assertThat(ContractController.class.getDeclaredMethod("list", com.tuowei.erp.commercial.contract.web.ContractPageQuery.class)
                .getAnnotation(GetMapping.class).value()).isEmpty();
    }

    private String permission(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ContractController.class.getDeclaredMethod(name, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        return annotation.value();
    }
}
