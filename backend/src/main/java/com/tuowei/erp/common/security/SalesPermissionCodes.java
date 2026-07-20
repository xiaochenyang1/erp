package com.tuowei.erp.common.security;

public interface SalesPermissionCodes {

    String SALES_ORDER_VIEW = "sales:order:view";
    String SALES_ORDER_CREATE = "sales:order:create";
    String SALES_ORDER_UPDATE = "sales:order:update";
    String SALES_ORDER_SUBMIT = "sales:order:submit";
    String SALES_ORDER_APPROVE = "sales:order:approve";
    String SALES_ORDER_UNAPPROVE = "sales:order:unapprove";
    String SALES_ORDER_REJECT = "sales:order:reject";
    String SALES_ORDER_CANCEL = "sales:order:cancel";

    String SALES_DELIVERY_VIEW = "sales:delivery:view";
    String SALES_DELIVERY_CREATE = "sales:delivery:create";
    String SALES_DELIVERY_UPDATE = "sales:delivery:update";
    String SALES_DELIVERY_CANCEL = "sales:delivery:cancel";
    String SALES_DELIVERY_POST = "sales:delivery:post";

    String SALES_RETURN_VIEW = "sales:return:view";
    String SALES_RETURN_CREATE = "sales:return:create";
    String SALES_RETURN_UPDATE = "sales:return:update";
    String SALES_RETURN_CANCEL = "sales:return:cancel";
    String SALES_RETURN_POST = "sales:return:post";

    String SALES_PRICE_VIEW = "sales:price:view";
    String SALES_PRICE_MANAGE = "sales:price:manage";
    String SALES_QUOTE_VIEW = "sales:quote:view";
    String SALES_QUOTE_MANAGE = "sales:quote:manage";

    String HAS_SALES_ORDER_VIEW = "hasAuthority('" + SALES_ORDER_VIEW + "')";
    String HAS_SALES_ORDER_CREATE = "hasAuthority('" + SALES_ORDER_CREATE + "')";
    String HAS_SALES_ORDER_UPDATE = "hasAuthority('" + SALES_ORDER_UPDATE + "')";
    String HAS_SALES_ORDER_SUBMIT = "hasAuthority('" + SALES_ORDER_SUBMIT + "')";
    String HAS_SALES_ORDER_APPROVE = "hasAuthority('" + SALES_ORDER_APPROVE + "')";
    String HAS_SALES_ORDER_UNAPPROVE = "hasAuthority('" + SALES_ORDER_UNAPPROVE + "')";
    String HAS_SALES_ORDER_REJECT = "hasAuthority('" + SALES_ORDER_REJECT + "')";
    String HAS_SALES_ORDER_CANCEL = "hasAuthority('" + SALES_ORDER_CANCEL + "')";

    String HAS_SALES_DELIVERY_VIEW = "hasAuthority('" + SALES_DELIVERY_VIEW + "')";
    String HAS_SALES_DELIVERY_CREATE = "hasAuthority('" + SALES_DELIVERY_CREATE + "')";
    String HAS_SALES_DELIVERY_UPDATE = "hasAuthority('" + SALES_DELIVERY_UPDATE + "')";
    String HAS_SALES_DELIVERY_CANCEL = "hasAuthority('" + SALES_DELIVERY_CANCEL + "')";
    String HAS_SALES_DELIVERY_POST = "hasAuthority('" + SALES_DELIVERY_POST + "')";

    String HAS_SALES_RETURN_VIEW = "hasAuthority('" + SALES_RETURN_VIEW + "')";
    String HAS_SALES_RETURN_CREATE = "hasAuthority('" + SALES_RETURN_CREATE + "')";
    String HAS_SALES_RETURN_UPDATE = "hasAuthority('" + SALES_RETURN_UPDATE + "')";
    String HAS_SALES_RETURN_CANCEL = "hasAuthority('" + SALES_RETURN_CANCEL + "')";
    String HAS_SALES_RETURN_POST = "hasAuthority('" + SALES_RETURN_POST + "')";

    String HAS_SALES_PRICE_VIEW = "hasAuthority('" + SALES_PRICE_VIEW + "')";
    String HAS_SALES_PRICE_MANAGE = "hasAuthority('" + SALES_PRICE_MANAGE + "')";
    String HAS_SALES_QUOTE_VIEW = "hasAuthority('" + SALES_QUOTE_VIEW + "')";
    String HAS_SALES_QUOTE_MANAGE = "hasAuthority('" + SALES_QUOTE_MANAGE + "')";
}
