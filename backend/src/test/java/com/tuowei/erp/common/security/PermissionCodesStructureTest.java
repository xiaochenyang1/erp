package com.tuowei.erp.common.security;

import com.tuowei.erp.finance.payment.controller.PaymentController;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.receipt.controller.ReceiptController;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.purchase.order.controller.PurchaseOrderController;
import com.tuowei.erp.sales.order.controller.SalesOrderController;
import com.tuowei.erp.workflow.controller.WorkflowController;
import com.tuowei.erp.workflow.web.WorkflowWithdrawRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCodesStructureTest {

    private static final Class<?>[] DOMAIN_INTERFACES = {
            SystemPermissionCodes.class,
            MasterdataPermissionCodes.class,
            PurchasePermissionCodes.class,
            InventoryPermissionCodes.class,
            ProductionPermissionCodes.class,
            SalesPermissionCodes.class,
            FinancePermissionCodes.class,
            ImportPermissionCodes.class,
            WorkflowPermissionCodes.class,
            ReportPermissionCodes.class,
            ExceptionTicketPermissionCodes.class,
            ExceptionRulePermissionCodes.class,
            ExceptionSlaPolicyPermissionCodes.class,
            QcPermissionCodes.class,
            ContractPermissionCodes.class
    };

    @Test
    void keepsPermissionCodesAsBackwardCompatibleFacade() {
        assertThat(PermissionCodes.class.getInterfaces())
                .containsExactlyInAnyOrder(DOMAIN_INTERFACES);

        assertThat(PermissionCodes.SYSTEM_USER_VIEW).isEqualTo(SystemPermissionCodes.SYSTEM_USER_VIEW);
        assertThat(PermissionCodes.INVENTORY_TRANSFER_POST).isEqualTo(InventoryPermissionCodes.INVENTORY_TRANSFER_POST);
        assertThat(PermissionCodes.FINANCE_LEDGER_VIEW).isEqualTo(FinancePermissionCodes.FINANCE_LEDGER_VIEW);
        assertThat(PermissionCodes.HAS_SYSTEM_USER_VIEW).isEqualTo("hasAuthority('system:user:view')");
    }

    @Test
    void permissionFacadeDoesNotDeclareDomainConstantsItself() {
        Set<String> facadeStringFields = Arrays.stream(PermissionCodes.class.getDeclaredFields())
                .filter(field -> field.getType().equals(String.class))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(facadeStringFields).isEmpty();
    }

    @Test
    void allPermissionsCollectsEveryDomainPermissionAndExcludesSecurityExpressions() {
        Set<String> expectedPermissions = Arrays.stream(DOMAIN_INTERFACES)
                .flatMap(domain -> Arrays.stream(domain.getFields()))
                .filter(field -> field.getType().equals(String.class))
                .filter(field -> !field.getName().startsWith("HAS_"))
                .map(PermissionCodesStructureTest::readStringField)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(PermissionCodes.allPermissions())
                .containsAll(expectedPermissions)
                .doesNotContain(PermissionCodes.HAS_SYSTEM_USER_VIEW)
                .doesNotContain(PermissionCodes.HAS_REPORT_VIEW);

        assertThat(PermissionCodes.allPermissions()).hasSameSizeAs(expectedPermissions);
    }

    @Test
    void financeCancellationUsesDedicatedPermissionsInsteadOfCreatePermissions() throws Exception {
        assertThat(PermissionCodes.allPermissions())
                .contains("finance:payment:cancel", "finance:receipt:cancel");

        assertThat(preAuthorizeValue(PaymentController.class, "cancel", Long.class, PaymentCancelRequest.class))
                .isEqualTo("hasAuthority('finance:payment:cancel')")
                .isNotEqualTo(PermissionCodes.HAS_FINANCE_PAYMENT_CREATE);
        assertThat(preAuthorizeValue(ReceiptController.class, "cancel", Long.class, ReceiptCancelRequest.class))
                .isEqualTo("hasAuthority('finance:receipt:cancel')")
                .isNotEqualTo(PermissionCodes.HAS_FINANCE_RECEIPT_CREATE);
    }

    @Test
    void workflowWithdrawUsesDedicatedPermissionInsteadOfViewPermission() throws Exception {
        assertThat(PermissionCodes.allPermissions())
                .contains("workflow:withdraw");

        assertThat(preAuthorizeValue(WorkflowController.class, "withdraw",
                String.class, Long.class, WorkflowWithdrawRequest.class))
                .isEqualTo("hasAuthority('workflow:withdraw')")
                .isNotEqualTo(PermissionCodes.HAS_WORKFLOW_VIEW);
    }

    @Test
    void purchaseOrderUnapproveUsesDedicatedPermissionInsteadOfApprovePermission() throws Exception {
        assertThat(PermissionCodes.allPermissions())
                .contains("purchase:order:unapprove");

        assertThat(preAuthorizeValue(PurchaseOrderController.class, "unapprove", Long.class))
                .isEqualTo("hasAuthority('purchase:order:unapprove')")
                .isNotEqualTo(PermissionCodes.HAS_PURCHASE_ORDER_APPROVE);
    }

    @Test
    void salesOrderUnapproveUsesDedicatedPermissionInsteadOfApprovePermission() throws Exception {
        assertThat(PermissionCodes.allPermissions())
                .contains("sales:order:unapprove");

        assertThat(preAuthorizeValue(SalesOrderController.class, "unapprove", Long.class))
                .isEqualTo("hasAuthority('sales:order:unapprove')")
                .isNotEqualTo(PermissionCodes.HAS_SALES_ORDER_APPROVE);
    }

    private static String readStringField(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("读取权限码测试字段失败", ex);
        }
    }

    private static String preAuthorizeValue(Class<?> controllerType, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = controllerType.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize)
                .as("%s.%s must declare method-level permission", controllerType.getSimpleName(), methodName)
                .isNotNull();
        return preAuthorize.value();
    }
}
