package com.tuowei.erp.common.security;

public interface ProductionPermissionCodes {

    String PRODUCTION_BOM_VIEW = "production:bom:view";
    String PRODUCTION_BOM_MANAGE = "production:bom:manage";
    String PRODUCTION_ORDER_VIEW = "production:order:view";
    String PRODUCTION_ORDER_CREATE = "production:order:create";
    String PRODUCTION_ORDER_UPDATE = "production:order:update";
    String PRODUCTION_ORDER_RELEASE = "production:order:release";
    String PRODUCTION_ORDER_ISSUE = "production:order:issue";
    String PRODUCTION_ORDER_COMPLETE = "production:order:complete";
    String PRODUCTION_ORDER_REVERSE_COMPLETION = "production:order:reverse-completion";
    String PRODUCTION_ORDER_RETURN = "production:order:return";
    String PRODUCTION_ORDER_CANCEL = "production:order:cancel";
    String PRODUCTION_ORDER_REPORT = "production:order:report";
    String PRODUCTION_WORK_CENTER_VIEW = "production:work-center:view";
    String PRODUCTION_WORK_CENTER_CREATE = "production:work-center:create";
    String PRODUCTION_WORK_CENTER_UPDATE = "production:work-center:update";
    String PRODUCTION_WORK_CENTER_ENABLE = "production:work-center:enable";
    String PRODUCTION_WORK_CENTER_DISABLE = "production:work-center:disable";
    String PRODUCTION_ROUTING_VIEW = "production:routing:view";
    String PRODUCTION_ROUTING_CREATE = "production:routing:create";
    String PRODUCTION_ROUTING_UPDATE = "production:routing:update";
    String PRODUCTION_ROUTING_ENABLE = "production:routing:enable";
    String PRODUCTION_ROUTING_DISABLE = "production:routing:disable";

    String HAS_PRODUCTION_BOM_VIEW = "hasAuthority('" + PRODUCTION_BOM_VIEW + "')";
    String HAS_PRODUCTION_BOM_MANAGE = "hasAuthority('" + PRODUCTION_BOM_MANAGE + "')";
    String HAS_PRODUCTION_ORDER_VIEW = "hasAuthority('" + PRODUCTION_ORDER_VIEW + "')";
    String HAS_PRODUCTION_ORDER_CREATE = "hasAuthority('" + PRODUCTION_ORDER_CREATE + "')";
    String HAS_PRODUCTION_ORDER_UPDATE = "hasAuthority('" + PRODUCTION_ORDER_UPDATE + "')";
    String HAS_PRODUCTION_ORDER_RELEASE = "hasAuthority('" + PRODUCTION_ORDER_RELEASE + "')";
    String HAS_PRODUCTION_ORDER_ISSUE = "hasAuthority('" + PRODUCTION_ORDER_ISSUE + "')";
    String HAS_PRODUCTION_ORDER_COMPLETE = "hasAuthority('" + PRODUCTION_ORDER_COMPLETE + "')";
    String HAS_PRODUCTION_ORDER_REVERSE_COMPLETION = "hasAuthority('" + PRODUCTION_ORDER_REVERSE_COMPLETION + "')";
    String HAS_PRODUCTION_ORDER_RETURN = "hasAuthority('" + PRODUCTION_ORDER_RETURN + "')";
    String HAS_PRODUCTION_ORDER_CANCEL = "hasAuthority('" + PRODUCTION_ORDER_CANCEL + "')";
    String HAS_PRODUCTION_ORDER_REPORT = "hasAuthority('" + PRODUCTION_ORDER_REPORT + "')";
    String HAS_PRODUCTION_WORK_CENTER_VIEW = "hasAuthority('" + PRODUCTION_WORK_CENTER_VIEW + "')";
    String HAS_PRODUCTION_WORK_CENTER_CREATE = "hasAuthority('" + PRODUCTION_WORK_CENTER_CREATE + "')";
    String HAS_PRODUCTION_WORK_CENTER_UPDATE = "hasAuthority('" + PRODUCTION_WORK_CENTER_UPDATE + "')";
    String HAS_PRODUCTION_WORK_CENTER_ENABLE = "hasAuthority('" + PRODUCTION_WORK_CENTER_ENABLE + "')";
    String HAS_PRODUCTION_WORK_CENTER_DISABLE = "hasAuthority('" + PRODUCTION_WORK_CENTER_DISABLE + "')";
    String HAS_PRODUCTION_ROUTING_VIEW = "hasAuthority('" + PRODUCTION_ROUTING_VIEW + "')";
    String HAS_PRODUCTION_ROUTING_CREATE = "hasAuthority('" + PRODUCTION_ROUTING_CREATE + "')";
    String HAS_PRODUCTION_ROUTING_UPDATE = "hasAuthority('" + PRODUCTION_ROUTING_UPDATE + "')";
    String HAS_PRODUCTION_ROUTING_ENABLE = "hasAuthority('" + PRODUCTION_ROUTING_ENABLE + "')";
    String HAS_PRODUCTION_ROUTING_DISABLE = "hasAuthority('" + PRODUCTION_ROUTING_DISABLE + "')";
}
