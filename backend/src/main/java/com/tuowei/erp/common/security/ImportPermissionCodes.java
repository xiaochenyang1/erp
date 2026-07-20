package com.tuowei.erp.common.security;

public interface ImportPermissionCodes {

    String IMPORT_INIT_MANAGE = "import:init:manage";

    String HAS_IMPORT_INIT_MANAGE = "hasAuthority('" + IMPORT_INIT_MANAGE + "')";
}
