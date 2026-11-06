package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.security.CurrentUserContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class MrpAccessPolicy {
    private final CurrentUserContext currentUserContext;
    public MrpAccessPolicy(CurrentUserContext currentUserContext) { this.currentUserContext = currentUserContext; }

    public void requireFullScope() {
        // Partial order visibility cannot produce a correct company-wide net plan or expose its order traces.
        if (!currentUserContext.requirePrincipal().dataScopeSnapshot().hasAllScope()) {
            throw new AccessDeniedException("MRP全账套计划需要全部数据范围权限");
        }
    }
    public void requireConversion(String type) {
        String permission = switch (type) {
            case "PURCHASE" -> "purchase:order:create";
            case "PRODUCTION" -> "production:order:create";
            default -> throw new IllegalArgumentException("未知MRP建议类型");
        };
        if (!currentUserContext.requirePrincipal().permissions().contains(permission)) {
            throw new AccessDeniedException("MRP转单还需要目标单据创建权限: " + permission);
        }
    }
}
