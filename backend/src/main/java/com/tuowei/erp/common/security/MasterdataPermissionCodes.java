package com.tuowei.erp.common.security;

public interface MasterdataPermissionCodes {

    String MASTERDATA_PRODUCT_VIEW = "masterdata:product:view";
    String MASTERDATA_PRODUCT_CREATE = "masterdata:product:create";
    String MASTERDATA_PRODUCT_UPDATE = "masterdata:product:update";
    String MASTERDATA_PRODUCT_ENABLE = "masterdata:product:enable";
    String MASTERDATA_PRODUCT_DISABLE = "masterdata:product:disable";

    String MASTERDATA_CUSTOMER_VIEW = "masterdata:customer:view";
    String MASTERDATA_CUSTOMER_CREATE = "masterdata:customer:create";
    String MASTERDATA_CUSTOMER_UPDATE = "masterdata:customer:update";
    String MASTERDATA_CUSTOMER_ENABLE = "masterdata:customer:enable";
    String MASTERDATA_CUSTOMER_DISABLE = "masterdata:customer:disable";

    String MASTERDATA_SUPPLIER_VIEW = "masterdata:supplier:view";
    String MASTERDATA_SUPPLIER_CREATE = "masterdata:supplier:create";
    String MASTERDATA_SUPPLIER_UPDATE = "masterdata:supplier:update";
    String MASTERDATA_SUPPLIER_ENABLE = "masterdata:supplier:enable";
    String MASTERDATA_SUPPLIER_DISABLE = "masterdata:supplier:disable";

    String MASTERDATA_WAREHOUSE_VIEW = "masterdata:warehouse:view";
    String MASTERDATA_WAREHOUSE_CREATE = "masterdata:warehouse:create";
    String MASTERDATA_WAREHOUSE_UPDATE = "masterdata:warehouse:update";
    String MASTERDATA_WAREHOUSE_ENABLE = "masterdata:warehouse:enable";
    String MASTERDATA_WAREHOUSE_DISABLE = "masterdata:warehouse:disable";

    String HAS_MASTERDATA_PRODUCT_VIEW = "hasAuthority('" + MASTERDATA_PRODUCT_VIEW + "')";
    String HAS_MASTERDATA_PRODUCT_CREATE = "hasAuthority('" + MASTERDATA_PRODUCT_CREATE + "')";
    String HAS_MASTERDATA_PRODUCT_UPDATE = "hasAuthority('" + MASTERDATA_PRODUCT_UPDATE + "')";
    String HAS_MASTERDATA_PRODUCT_ENABLE = "hasAuthority('" + MASTERDATA_PRODUCT_ENABLE + "')";
    String HAS_MASTERDATA_PRODUCT_DISABLE = "hasAuthority('" + MASTERDATA_PRODUCT_DISABLE + "')";

    String HAS_MASTERDATA_CUSTOMER_VIEW = "hasAuthority('" + MASTERDATA_CUSTOMER_VIEW + "')";
    String HAS_MASTERDATA_CUSTOMER_CREATE = "hasAuthority('" + MASTERDATA_CUSTOMER_CREATE + "')";
    String HAS_MASTERDATA_CUSTOMER_UPDATE = "hasAuthority('" + MASTERDATA_CUSTOMER_UPDATE + "')";
    String HAS_MASTERDATA_CUSTOMER_ENABLE = "hasAuthority('" + MASTERDATA_CUSTOMER_ENABLE + "')";
    String HAS_MASTERDATA_CUSTOMER_DISABLE = "hasAuthority('" + MASTERDATA_CUSTOMER_DISABLE + "')";

    String HAS_MASTERDATA_SUPPLIER_VIEW = "hasAuthority('" + MASTERDATA_SUPPLIER_VIEW + "')";
    String HAS_MASTERDATA_SUPPLIER_CREATE = "hasAuthority('" + MASTERDATA_SUPPLIER_CREATE + "')";
    String HAS_MASTERDATA_SUPPLIER_UPDATE = "hasAuthority('" + MASTERDATA_SUPPLIER_UPDATE + "')";
    String HAS_MASTERDATA_SUPPLIER_ENABLE = "hasAuthority('" + MASTERDATA_SUPPLIER_ENABLE + "')";
    String HAS_MASTERDATA_SUPPLIER_DISABLE = "hasAuthority('" + MASTERDATA_SUPPLIER_DISABLE + "')";

    String HAS_MASTERDATA_WAREHOUSE_VIEW = "hasAuthority('" + MASTERDATA_WAREHOUSE_VIEW + "')";
    String HAS_MASTERDATA_WAREHOUSE_CREATE = "hasAuthority('" + MASTERDATA_WAREHOUSE_CREATE + "')";
    String HAS_MASTERDATA_WAREHOUSE_UPDATE = "hasAuthority('" + MASTERDATA_WAREHOUSE_UPDATE + "')";
    String HAS_MASTERDATA_WAREHOUSE_ENABLE = "hasAuthority('" + MASTERDATA_WAREHOUSE_ENABLE + "')";
    String HAS_MASTERDATA_WAREHOUSE_DISABLE = "hasAuthority('" + MASTERDATA_WAREHOUSE_DISABLE + "')";
}
