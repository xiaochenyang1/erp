package com.tuowei.erp.common.config;

import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.service.InventoryAlertQueryService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.auth.service.UserSessionService;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.config.service.SequenceRuleService;
import com.tuowei.erp.system.config.service.SystemConfigService;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.dept.service.DeptService;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.dict.service.SystemDictQueryService;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.service.SystemLogQueryService;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.LoginLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.service.MenuQueryService;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.notification.service.NotificationQueryService;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import com.tuowei.erp.system.post.service.PostService;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.readiness.service.ReadinessService;
import com.tuowei.erp.system.readiness.service.ReadinessCommandService;
import com.tuowei.erp.system.readiness.service.ReadinessQueryService;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.service.RoleQueryService;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.user.service.UserQueryService;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.UserPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyTransactionStructureTest {

    @Test
    void systemLogQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(SystemLogService.class.getMethod("listLoginLogs", LoginLogPageQuery.class));
        assertReadOnly(SystemLogService.class.getMethod("listOperationLogs", OperationLogPageQuery.class));
        assertReadOnly(SystemLogService.class.getMethod("getOperationLog", Long.class));
        assertReadOnly(SystemLogService.class.getMethod("listAuditLogs", AuditLogPageQuery.class));
        assertReadOnly(SystemLogQueryService.class.getMethod("listLoginLogs", LoginLogPageQuery.class));
        assertReadOnly(SystemLogQueryService.class.getMethod("listOperationLogs", OperationLogPageQuery.class));
        assertReadOnly(SystemLogQueryService.class.getMethod("getOperationLog", Long.class));
        assertReadOnly(SystemLogQueryService.class.getMethod("listAuditLogs", AuditLogPageQuery.class));
    }

    @Test
    void inventoryAlertLowStockQueryUsesReadOnlyTransaction() throws NoSuchMethodException {
        assertReadOnly(InventoryAlertService.class.getMethod("listLowStock", Long.class, Long.class));
        assertReadOnly(InventoryAlertQueryService.class.getMethod("listLowStock", Long.class, Long.class));
    }

    @Test
    void attachmentQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(AttachmentService.class, "list", AttachmentPageQuery.class);
        assertReadOnly(AttachmentService.class, "download", Long.class);
    }

    @Test
    void readinessQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ReadinessService.class, "listRuns", ReadinessRunPageQuery.class);
        assertReadOnly(ReadinessService.class, "detail", Long.class);
        assertReadOnly(ReadinessQueryService.class, "listRuns", ReadinessRunPageQuery.class);
        assertReadOnly(ReadinessQueryService.class, "detail", Long.class);
    }

    @Test
    void readinessCommandsUseRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] writeServices = {ReadinessService.class, ReadinessCommandService.class};
        for (Class<?> serviceClass : writeServices) {
            assertRequiredWrite(serviceClass, "createRun", ReadinessRunCreateRequest.class);
            assertRequiredWrite(serviceClass, "addItem", Long.class, ReadinessItemCreateRequest.class);
            assertRequiredWrite(serviceClass, "addEvidence", Long.class, ReadinessEvidenceCreateRequest.class);
            assertRequiredWrite(serviceClass, "markItemResult", Long.class, ReadinessItemResultRequest.class);
            assertRequiredWrite(serviceClass, "decide", Long.class, ReadinessDecisionRequest.class);
            assertRequiredWrite(serviceClass, "recordPreflightEvidence", Long.class, ReadinessPreflightResponse.class);
        }
    }

    @Test
    void systemAdministrationQueriesUseReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(UserService.class, "list", UserPageQuery.class);
        assertReadOnly(UserService.class, "getById", Long.class);
        assertReadOnly(UserService.class, "getAssignedRoles", Long.class);
        assertReadOnly(UserQueryService.class, "list", UserPageQuery.class);
        assertReadOnly(UserQueryService.class, "getById", Long.class);
        assertReadOnly(UserQueryService.class, "getAssignedRoles", Long.class);

        assertReadOnly(RoleService.class, "list", RolePageQuery.class);
        assertReadOnly(RoleService.class, "getById", Long.class);
        assertReadOnly(RoleService.class, "getAssignedMenus", Long.class);
        assertReadOnly(RoleQueryService.class, "list", RolePageQuery.class);
        assertReadOnly(RoleQueryService.class, "getById", Long.class);
        assertReadOnly(RoleQueryService.class, "getAssignedMenus", Long.class);

        assertReadOnly(MenuService.class, "list", MenuPageQuery.class);
        assertReadOnly(MenuService.class, "tree");
        assertReadOnly(MenuService.class, "runtimeTreeForCurrentUser");
        assertReadOnly(MenuService.class, "getById", Long.class);
        assertReadOnly(MenuQueryService.class, "list", MenuPageQuery.class);
        assertReadOnly(MenuQueryService.class, "tree");
        assertReadOnly(MenuQueryService.class, "runtimeTreeForCurrentUser");
        assertReadOnly(MenuQueryService.class, "getById", Long.class);

        assertReadOnly(DeptService.class, "list", DeptPageQuery.class);
        assertReadOnly(DeptService.class, "tree");
        assertReadOnly(DeptService.class, "getById", Long.class);

        assertReadOnly(PostService.class, "list", PostPageQuery.class);
        assertReadOnly(PostService.class, "getById", Long.class);

        assertReadOnly(SystemDictService.class, "listTypes", DictTypePageQuery.class);
        assertReadOnly(SystemDictService.class, "getTypeById", Long.class);
        assertReadOnly(SystemDictService.class, "listItems", String.class);
        assertReadOnly(SystemDictService.class, "requireEnabledItem", String.class, String.class, String.class);
        assertReadOnly(SystemDictQueryService.class, "listTypes", DictTypePageQuery.class);
        assertReadOnly(SystemDictQueryService.class, "getTypeById", Long.class);
        assertReadOnly(SystemDictQueryService.class, "listItems", String.class);
        assertReadOnly(SystemDictQueryService.class, "requireEnabledItem", String.class, String.class, String.class);

        assertReadOnly(SystemConfigService.class, "list", SystemConfigPageQuery.class);
        assertReadOnly(SystemConfigService.class, "getById", Long.class);

        assertReadOnly(SequenceRuleService.class, "list", SequenceRulePageQuery.class);
        assertReadOnly(SequenceRuleService.class, "getById", Long.class);

        assertReadOnly(UserSessionService.class, "list", UserSessionPageQuery.class);

        assertReadOnly(NotificationService.class, "listMine", NotificationPageQuery.class);
        assertReadOnly(NotificationService.class, "countUnreadMine");
        assertReadOnly(NotificationQueryService.class, "listMine", NotificationPageQuery.class);
        assertReadOnly(NotificationQueryService.class, "countUnreadMine");
    }

    private static void assertReadOnly(Class<?> serviceClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        assertReadOnly(serviceClass.getMethod(methodName, parameterTypes));
    }

    private static void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s should declare @Transactional(readOnly = true)", method)
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s should be read-only", method)
                .isTrue();
    }

    private static void assertRequiredWrite(Class<?> serviceClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Transactional transactional = serviceClass.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s.%s should declare a required write transaction", serviceClass.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
