package com.tuowei.erp.common.security;

public interface ReportPermissionCodes {

    String REPORT_VIEW = "report:view";

    String HAS_REPORT_VIEW = "hasAuthority('" + REPORT_VIEW + "')";
}
