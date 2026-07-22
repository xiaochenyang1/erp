package com.tuowei.erp.common.security;

public interface WorkflowPermissionCodes {

    String WORKFLOW_VIEW = "workflow:view";
    String WORKFLOW_APPROVE = "workflow:approve";
    String WORKFLOW_REJECT = "workflow:reject";
    String WORKFLOW_WITHDRAW = "workflow:withdraw";
    String WORKFLOW_ESCALATE = "workflow:escalate";
    String WORKFLOW_CONFIG_VIEW = "workflow:config:view";
    String WORKFLOW_CONFIG_UPDATE = "workflow:config:update";

    String HAS_WORKFLOW_VIEW = "hasAuthority('" + WORKFLOW_VIEW + "')";
    String HAS_WORKFLOW_APPROVE = "hasAuthority('" + WORKFLOW_APPROVE + "')";
    String HAS_WORKFLOW_REJECT = "hasAuthority('" + WORKFLOW_REJECT + "')";
    String HAS_WORKFLOW_WITHDRAW = "hasAuthority('" + WORKFLOW_WITHDRAW + "')";
    String HAS_WORKFLOW_ESCALATE = "hasAuthority('" + WORKFLOW_ESCALATE + "')";
    String HAS_WORKFLOW_CONFIG_VIEW = "hasAuthority('" + WORKFLOW_CONFIG_VIEW + "')";
    String HAS_WORKFLOW_CONFIG_UPDATE = "hasAuthority('" + WORKFLOW_CONFIG_UPDATE + "')";
}
