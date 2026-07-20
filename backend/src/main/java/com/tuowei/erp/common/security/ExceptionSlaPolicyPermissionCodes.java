package com.tuowei.erp.common.security;

public interface ExceptionSlaPolicyPermissionCodes {

    String EXCEPTION_SLA_POLICY_VIEW = "exception-sla-policy:view";
    String EXCEPTION_SLA_POLICY_MANAGE = "exception-sla-policy:manage";

    String HAS_EXCEPTION_SLA_POLICY_VIEW = "hasAuthority('" + EXCEPTION_SLA_POLICY_VIEW + "')";
    String HAS_EXCEPTION_SLA_POLICY_MANAGE = "hasAuthority('" + EXCEPTION_SLA_POLICY_MANAGE + "')";
}
