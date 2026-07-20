package com.tuowei.erp.common.security;

public interface SystemPermissionCodes {

    String SYSTEM_PROFILE_VIEW = "system:profile:view";

    String SYSTEM_USER_VIEW = "system:user:view";
    String SYSTEM_USER_CREATE = "system:user:create";
    String SYSTEM_USER_UPDATE = "system:user:update";
    String SYSTEM_USER_ENABLE = "system:user:enable";
    String SYSTEM_USER_DISABLE = "system:user:disable";
    String SYSTEM_USER_ASSIGN_ROLE = "system:user:assign-role";
    String SYSTEM_USER_ASSIGN_DATA_SCOPE = "system:user:assign-data-scope";
    String SYSTEM_USER_RESET_PASSWORD = "system:user:reset-password";
    String SYSTEM_USER_SESSION_VIEW = "system:user-session:view";
    String SYSTEM_USER_SESSION_REVOKE = "system:user-session:revoke";

    String SYSTEM_ROLE_VIEW = "system:role:view";
    String SYSTEM_ROLE_CREATE = "system:role:create";
    String SYSTEM_ROLE_UPDATE = "system:role:update";
    String SYSTEM_ROLE_ENABLE = "system:role:enable";
    String SYSTEM_ROLE_DISABLE = "system:role:disable";
    String SYSTEM_ROLE_ASSIGN_MENU = "system:role:assign-menu";
    String SYSTEM_ROLE_ASSIGN_DATA_SCOPE = "system:role:assign-data-scope";

    String SYSTEM_MENU_VIEW = "system:menu:view";
    String SYSTEM_MENU_CREATE = "system:menu:create";
    String SYSTEM_MENU_UPDATE = "system:menu:update";
    String SYSTEM_MENU_ENABLE = "system:menu:enable";
    String SYSTEM_MENU_DISABLE = "system:menu:disable";

    String SYSTEM_DEPT_VIEW = "system:dept:view";
    String SYSTEM_DEPT_CREATE = "system:dept:create";
    String SYSTEM_DEPT_UPDATE = "system:dept:update";
    String SYSTEM_DEPT_ENABLE = "system:dept:enable";
    String SYSTEM_DEPT_DISABLE = "system:dept:disable";

    String SYSTEM_POST_VIEW = "system:post:view";
    String SYSTEM_POST_CREATE = "system:post:create";
    String SYSTEM_POST_UPDATE = "system:post:update";
    String SYSTEM_POST_ENABLE = "system:post:enable";
    String SYSTEM_POST_DISABLE = "system:post:disable";

    String SYSTEM_CONFIG_VIEW = "system:config:view";
    String SYSTEM_CONFIG_CREATE = "system:config:create";
    String SYSTEM_CONFIG_UPDATE = "system:config:update";
    String SYSTEM_CONFIG_ENABLE = "system:config:enable";
    String SYSTEM_CONFIG_DISABLE = "system:config:disable";

    String SYSTEM_SEQUENCE_RULE_VIEW = "system:sequence-rule:view";
    String SYSTEM_SEQUENCE_RULE_CREATE = "system:sequence-rule:create";
    String SYSTEM_SEQUENCE_RULE_UPDATE = "system:sequence-rule:update";
    String SYSTEM_SEQUENCE_RULE_ENABLE = "system:sequence-rule:enable";
    String SYSTEM_SEQUENCE_RULE_DISABLE = "system:sequence-rule:disable";

    String SYSTEM_DICT_VIEW = "system:dict:view";
    String SYSTEM_DICT_CREATE = "system:dict:create";
    String SYSTEM_DICT_UPDATE = "system:dict:update";
    String SYSTEM_DICT_ENABLE = "system:dict:enable";
    String SYSTEM_DICT_DISABLE = "system:dict:disable";
    String SYSTEM_LOG_VIEW = "system:log:view";
    String SYSTEM_ATTACHMENT_VIEW = "system:attachment:view";
    String SYSTEM_ATTACHMENT_MANAGE = "system:attachment:manage";
    String SYSTEM_ATTACHMENT_DELETE = "system:attachment:delete";
    String SYSTEM_NOTIFICATION_VIEW = "system:notification:view";
    String SYSTEM_NOTIFICATION_MANAGE = "system:notification:manage";
    String SYSTEM_READINESS_VIEW = "system:readiness:view";
    String SYSTEM_READINESS_MANAGE = "system:readiness:manage";
    String SYSTEM_READINESS_DECIDE = "system:readiness:decide";
    String SYSTEM_OBSERVABILITY_VIEW = "system:observability:view";

    String HAS_SYSTEM_PROFILE_VIEW = "hasAuthority('" + SYSTEM_PROFILE_VIEW + "')";

    String HAS_SYSTEM_USER_VIEW = "hasAuthority('" + SYSTEM_USER_VIEW + "')";
    String HAS_SYSTEM_USER_CREATE = "hasAuthority('" + SYSTEM_USER_CREATE + "')";
    String HAS_SYSTEM_USER_UPDATE = "hasAuthority('" + SYSTEM_USER_UPDATE + "')";
    String HAS_SYSTEM_USER_ENABLE = "hasAuthority('" + SYSTEM_USER_ENABLE + "')";
    String HAS_SYSTEM_USER_DISABLE = "hasAuthority('" + SYSTEM_USER_DISABLE + "')";
    String HAS_SYSTEM_USER_ASSIGN_ROLE = "hasAuthority('" + SYSTEM_USER_ASSIGN_ROLE + "')";
    String HAS_SYSTEM_USER_ASSIGN_DATA_SCOPE = "hasAuthority('" + SYSTEM_USER_ASSIGN_DATA_SCOPE + "')";
    String HAS_SYSTEM_USER_RESET_PASSWORD = "hasAuthority('" + SYSTEM_USER_RESET_PASSWORD + "')";
    String HAS_SYSTEM_USER_SESSION_VIEW = "hasAuthority('" + SYSTEM_USER_SESSION_VIEW + "')";
    String HAS_SYSTEM_USER_SESSION_REVOKE = "hasAuthority('" + SYSTEM_USER_SESSION_REVOKE + "')";

    String HAS_SYSTEM_ROLE_VIEW = "hasAuthority('" + SYSTEM_ROLE_VIEW + "')";
    String HAS_SYSTEM_ROLE_CREATE = "hasAuthority('" + SYSTEM_ROLE_CREATE + "')";
    String HAS_SYSTEM_ROLE_UPDATE = "hasAuthority('" + SYSTEM_ROLE_UPDATE + "')";
    String HAS_SYSTEM_ROLE_ENABLE = "hasAuthority('" + SYSTEM_ROLE_ENABLE + "')";
    String HAS_SYSTEM_ROLE_DISABLE = "hasAuthority('" + SYSTEM_ROLE_DISABLE + "')";
    String HAS_SYSTEM_ROLE_ASSIGN_MENU = "hasAuthority('" + SYSTEM_ROLE_ASSIGN_MENU + "')";
    String HAS_SYSTEM_ROLE_ASSIGN_DATA_SCOPE = "hasAuthority('" + SYSTEM_ROLE_ASSIGN_DATA_SCOPE + "')";

    String HAS_SYSTEM_MENU_VIEW = "hasAuthority('" + SYSTEM_MENU_VIEW + "')";
    String HAS_SYSTEM_MENU_CREATE = "hasAuthority('" + SYSTEM_MENU_CREATE + "')";
    String HAS_SYSTEM_MENU_UPDATE = "hasAuthority('" + SYSTEM_MENU_UPDATE + "')";
    String HAS_SYSTEM_MENU_ENABLE = "hasAuthority('" + SYSTEM_MENU_ENABLE + "')";
    String HAS_SYSTEM_MENU_DISABLE = "hasAuthority('" + SYSTEM_MENU_DISABLE + "')";

    String HAS_SYSTEM_DEPT_VIEW = "hasAuthority('" + SYSTEM_DEPT_VIEW + "')";
    String HAS_SYSTEM_DEPT_CREATE = "hasAuthority('" + SYSTEM_DEPT_CREATE + "')";
    String HAS_SYSTEM_DEPT_UPDATE = "hasAuthority('" + SYSTEM_DEPT_UPDATE + "')";
    String HAS_SYSTEM_DEPT_ENABLE = "hasAuthority('" + SYSTEM_DEPT_ENABLE + "')";
    String HAS_SYSTEM_DEPT_DISABLE = "hasAuthority('" + SYSTEM_DEPT_DISABLE + "')";

    String HAS_SYSTEM_POST_VIEW = "hasAuthority('" + SYSTEM_POST_VIEW + "')";
    String HAS_SYSTEM_POST_CREATE = "hasAuthority('" + SYSTEM_POST_CREATE + "')";
    String HAS_SYSTEM_POST_UPDATE = "hasAuthority('" + SYSTEM_POST_UPDATE + "')";
    String HAS_SYSTEM_POST_ENABLE = "hasAuthority('" + SYSTEM_POST_ENABLE + "')";
    String HAS_SYSTEM_POST_DISABLE = "hasAuthority('" + SYSTEM_POST_DISABLE + "')";

    String HAS_SYSTEM_CONFIG_VIEW = "hasAuthority('" + SYSTEM_CONFIG_VIEW + "')";
    String HAS_SYSTEM_CONFIG_CREATE = "hasAuthority('" + SYSTEM_CONFIG_CREATE + "')";
    String HAS_SYSTEM_CONFIG_UPDATE = "hasAuthority('" + SYSTEM_CONFIG_UPDATE + "')";
    String HAS_SYSTEM_CONFIG_ENABLE = "hasAuthority('" + SYSTEM_CONFIG_ENABLE + "')";
    String HAS_SYSTEM_CONFIG_DISABLE = "hasAuthority('" + SYSTEM_CONFIG_DISABLE + "')";

    String HAS_SYSTEM_SEQUENCE_RULE_VIEW = "hasAuthority('" + SYSTEM_SEQUENCE_RULE_VIEW + "')";
    String HAS_SYSTEM_SEQUENCE_RULE_CREATE = "hasAuthority('" + SYSTEM_SEQUENCE_RULE_CREATE + "')";
    String HAS_SYSTEM_SEQUENCE_RULE_UPDATE = "hasAuthority('" + SYSTEM_SEQUENCE_RULE_UPDATE + "')";
    String HAS_SYSTEM_SEQUENCE_RULE_ENABLE = "hasAuthority('" + SYSTEM_SEQUENCE_RULE_ENABLE + "')";
    String HAS_SYSTEM_SEQUENCE_RULE_DISABLE = "hasAuthority('" + SYSTEM_SEQUENCE_RULE_DISABLE + "')";

    String HAS_SYSTEM_DICT_VIEW = "hasAuthority('" + SYSTEM_DICT_VIEW + "')";
    String HAS_SYSTEM_DICT_CREATE = "hasAuthority('" + SYSTEM_DICT_CREATE + "')";
    String HAS_SYSTEM_DICT_UPDATE = "hasAuthority('" + SYSTEM_DICT_UPDATE + "')";
    String HAS_SYSTEM_DICT_ENABLE = "hasAuthority('" + SYSTEM_DICT_ENABLE + "')";
    String HAS_SYSTEM_DICT_DISABLE = "hasAuthority('" + SYSTEM_DICT_DISABLE + "')";
    String HAS_SYSTEM_LOG_VIEW = "hasAuthority('" + SYSTEM_LOG_VIEW + "')";
    String HAS_SYSTEM_ATTACHMENT_VIEW = "hasAuthority('" + SYSTEM_ATTACHMENT_VIEW + "')";
    String HAS_SYSTEM_ATTACHMENT_MANAGE = "hasAuthority('" + SYSTEM_ATTACHMENT_MANAGE + "')";
    String HAS_SYSTEM_ATTACHMENT_DELETE = "hasAuthority('" + SYSTEM_ATTACHMENT_DELETE + "')";
    String HAS_SYSTEM_NOTIFICATION_VIEW = "hasAuthority('" + SYSTEM_NOTIFICATION_VIEW + "')";
    String HAS_SYSTEM_NOTIFICATION_MANAGE = "hasAuthority('" + SYSTEM_NOTIFICATION_MANAGE + "')";
    String HAS_SYSTEM_READINESS_VIEW = "hasAuthority('" + SYSTEM_READINESS_VIEW + "')";
    String HAS_SYSTEM_READINESS_MANAGE = "hasAuthority('" + SYSTEM_READINESS_MANAGE + "')";
    String HAS_SYSTEM_READINESS_DECIDE = "hasAuthority('" + SYSTEM_READINESS_DECIDE + "')";
    String HAS_SYSTEM_OBSERVABILITY_VIEW = "hasAuthority('" + SYSTEM_OBSERVABILITY_VIEW + "')";
}
