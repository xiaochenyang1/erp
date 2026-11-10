package com.tuowei.erp.common.security;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class PermissionCodes implements
        SystemPermissionCodes,
        MasterdataPermissionCodes,
        PurchasePermissionCodes,
        InventoryPermissionCodes,
        ProductionPermissionCodes,
        SalesPermissionCodes,
        FinancePermissionCodes,
        ImportPermissionCodes,
        WorkflowPermissionCodes,
        ReportPermissionCodes,
        ExceptionTicketPermissionCodes,
        ExceptionRulePermissionCodes,
        ExceptionSlaPolicyPermissionCodes,
        QcPermissionCodes,
        ContractPermissionCodes {

    private static final Set<String> ALL_PERMISSIONS = collectAllPermissions();

    private PermissionCodes() {
    }

    public static Set<String> allPermissions() {
        return ALL_PERMISSIONS;
    }

    private static Set<String> collectAllPermissions() {
        return Arrays.stream(PermissionCodes.class.getFields())
                .filter(field -> field.getType().equals(String.class))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isFinal(field.getModifiers()))
                .filter(field -> !field.getName().startsWith("HAS_"))
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException("读取权限码失败", ex);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }
}
