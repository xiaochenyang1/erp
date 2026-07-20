package com.tuowei.erp.common.security;

public interface ExceptionTicketPermissionCodes {

    String EXCEPTION_TICKET_VIEW = "exception-ticket:view";
    String EXCEPTION_TICKET_MANAGE = "exception-ticket:manage";

    String HAS_EXCEPTION_TICKET_VIEW = "hasAuthority('" + EXCEPTION_TICKET_VIEW + "')";
    String HAS_EXCEPTION_TICKET_MANAGE = "hasAuthority('" + EXCEPTION_TICKET_MANAGE + "')";
}
