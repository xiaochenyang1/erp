package com.tuowei.erp.common.security;

public interface ExceptionRulePermissionCodes {

    String EXCEPTION_RULE_VIEW = "exception-rule:view";
    String EXCEPTION_RULE_MANAGE = "exception-rule:manage";
    String EXCEPTION_RULE_EXECUTE = "exception-rule:execute";

    String HAS_EXCEPTION_RULE_VIEW = "hasAuthority('" + EXCEPTION_RULE_VIEW + "')";
    String HAS_EXCEPTION_RULE_MANAGE = "hasAuthority('" + EXCEPTION_RULE_MANAGE + "')";
    String HAS_EXCEPTION_RULE_EXECUTE = "hasAuthority('" + EXCEPTION_RULE_EXECUTE + "')";
}
