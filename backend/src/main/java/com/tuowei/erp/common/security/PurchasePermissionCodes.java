package com.tuowei.erp.common.security;

public interface PurchasePermissionCodes {

    String PURCHASE_ORDER_VIEW = "purchase:order:view";
    String PURCHASE_ORDER_CREATE = "purchase:order:create";
    String PURCHASE_ORDER_UPDATE = "purchase:order:update";
    String PURCHASE_ORDER_SUBMIT = "purchase:order:submit";
    String PURCHASE_ORDER_APPROVE = "purchase:order:approve";
    String PURCHASE_ORDER_UNAPPROVE = "purchase:order:unapprove";
    String PURCHASE_ORDER_REJECT = "purchase:order:reject";
    String PURCHASE_ORDER_CANCEL = "purchase:order:cancel";
    String PURCHASE_ORDER_CLOSE = "purchase:order:close";

    String PURCHASE_RECEIPT_VIEW = "purchase:receipt:view";
    String PURCHASE_RECEIPT_CREATE = "purchase:receipt:create";
    String PURCHASE_RECEIPT_UPDATE = "purchase:receipt:update";
    String PURCHASE_RECEIPT_CANCEL = "purchase:receipt:cancel";
    String PURCHASE_RECEIPT_POST = "purchase:receipt:post";

    String PURCHASE_RETURN_VIEW = "purchase:return:view";
    String PURCHASE_RETURN_CREATE = "purchase:return:create";
    String PURCHASE_RETURN_UPDATE = "purchase:return:update";
    String PURCHASE_RETURN_CANCEL = "purchase:return:cancel";
    String PURCHASE_RETURN_POST = "purchase:return:post";

    String PURCHASE_INQUIRY_VIEW = "purchase:inquiry:view";
    String PURCHASE_INQUIRY_MANAGE = "purchase:inquiry:manage";

    String PURCHASE_PRICE_VIEW = "purchase:price:view";
    String PURCHASE_PRICE_MANAGE = "purchase:price:manage";

    String HAS_PURCHASE_ORDER_VIEW = "hasAuthority('" + PURCHASE_ORDER_VIEW + "')";
    String HAS_PURCHASE_ORDER_CREATE = "hasAuthority('" + PURCHASE_ORDER_CREATE + "')";
    String HAS_PURCHASE_ORDER_UPDATE = "hasAuthority('" + PURCHASE_ORDER_UPDATE + "')";
    String HAS_PURCHASE_ORDER_SUBMIT = "hasAuthority('" + PURCHASE_ORDER_SUBMIT + "')";
    String HAS_PURCHASE_ORDER_APPROVE = "hasAuthority('" + PURCHASE_ORDER_APPROVE + "')";
    String HAS_PURCHASE_ORDER_UNAPPROVE = "hasAuthority('" + PURCHASE_ORDER_UNAPPROVE + "')";
    String HAS_PURCHASE_ORDER_REJECT = "hasAuthority('" + PURCHASE_ORDER_REJECT + "')";
    String HAS_PURCHASE_ORDER_CANCEL = "hasAuthority('" + PURCHASE_ORDER_CANCEL + "')";
    String HAS_PURCHASE_ORDER_CLOSE = "hasAuthority('" + PURCHASE_ORDER_CLOSE + "')";

    String HAS_PURCHASE_RECEIPT_VIEW = "hasAuthority('" + PURCHASE_RECEIPT_VIEW + "')";
    String HAS_PURCHASE_RECEIPT_CREATE = "hasAuthority('" + PURCHASE_RECEIPT_CREATE + "')";
    String HAS_PURCHASE_RECEIPT_UPDATE = "hasAuthority('" + PURCHASE_RECEIPT_UPDATE + "')";
    String HAS_PURCHASE_RECEIPT_CANCEL = "hasAuthority('" + PURCHASE_RECEIPT_CANCEL + "')";
    String HAS_PURCHASE_RECEIPT_POST = "hasAuthority('" + PURCHASE_RECEIPT_POST + "')";

    String HAS_PURCHASE_RETURN_VIEW = "hasAuthority('" + PURCHASE_RETURN_VIEW + "')";
    String HAS_PURCHASE_RETURN_CREATE = "hasAuthority('" + PURCHASE_RETURN_CREATE + "')";
    String HAS_PURCHASE_RETURN_UPDATE = "hasAuthority('" + PURCHASE_RETURN_UPDATE + "')";
    String HAS_PURCHASE_RETURN_CANCEL = "hasAuthority('" + PURCHASE_RETURN_CANCEL + "')";
    String HAS_PURCHASE_RETURN_POST = "hasAuthority('" + PURCHASE_RETURN_POST + "')";

    String HAS_PURCHASE_INQUIRY_VIEW = "hasAuthority('" + PURCHASE_INQUIRY_VIEW + "')";
    String HAS_PURCHASE_INQUIRY_MANAGE = "hasAuthority('" + PURCHASE_INQUIRY_MANAGE + "')";

    String HAS_PURCHASE_PRICE_VIEW = "hasAuthority('" + PURCHASE_PRICE_VIEW + "')";
    String HAS_PURCHASE_PRICE_MANAGE = "hasAuthority('" + PURCHASE_PRICE_MANAGE + "')";
}
