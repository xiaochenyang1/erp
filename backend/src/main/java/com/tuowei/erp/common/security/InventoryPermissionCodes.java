package com.tuowei.erp.common.security;

public interface InventoryPermissionCodes {

    String INVENTORY_STOCK_VIEW = "inventory:stock:view";
    String INVENTORY_ADJUSTMENT_VIEW = "inventory:adjustment:view";
    String INVENTORY_ADJUSTMENT_CREATE = "inventory:adjustment:create";
    String INVENTORY_ADJUSTMENT_POST = "inventory:adjustment:post";
    String INVENTORY_CHECK_VIEW = "inventory:check:view";
    String INVENTORY_CHECK_CREATE = "inventory:check:create";
    String INVENTORY_CHECK_ADJUST = "inventory:check:adjust";
    String INVENTORY_ALERT_VIEW = "inventory:alert:view";
    String INVENTORY_ALERT_CREATE = "inventory:alert:create";
    String INVENTORY_ALERT_HANDLE = "inventory:alert:handle";
    String INVENTORY_TRANSFER_VIEW = "inventory:transfer:view";
    String INVENTORY_TRANSFER_CREATE = "inventory:transfer:create";
    String INVENTORY_TRANSFER_POST = "inventory:transfer:post";
    String INVENTORY_RESERVATION_VIEW = "inventory:reservation:view";
    String INVENTORY_RESERVATION_CHECK = "inventory:reservation:check";
    String INVENTORY_RESERVATION_RELEASE = "inventory:reservation:release";
    String INVENTORY_ADJUSTMENT_CANCEL = "inventory:adjustment:cancel";
    String INVENTORY_CHECK_CANCEL = "inventory:check:cancel";
    String INVENTORY_TRANSFER_CANCEL = "inventory:transfer:cancel";
    String INVENTORY_REPLENISHMENT_VIEW = "inventory:replenishment:view";
    String INVENTORY_REPLENISHMENT_CREATE = "inventory:replenishment:create";
    String INVENTORY_REPLENISHMENT_UPDATE = "inventory:replenishment:update";
    String INVENTORY_REPLENISHMENT_CANCEL = "inventory:replenishment:cancel";
    String INVENTORY_REPLENISHMENT_CONVERT = "inventory:replenishment:convert";
    String INVENTORY_MRP_VIEW = "inventory:mrp:view";
    String INVENTORY_MRP_RUN = "inventory:mrp:run";
    String INVENTORY_MRP_CONVERT = "inventory:mrp:convert";
    String INVENTORY_LOT_GENEALOGY = "inventory:lot:genealogy";

    String INVENTORY_SERIAL_VIEW = "inventory:serial:view";
    String INVENTORY_SERIAL_MANAGE = "inventory:serial:manage";

    String HAS_INVENTORY_STOCK_VIEW = "hasAuthority('" + INVENTORY_STOCK_VIEW + "')";
    String HAS_INVENTORY_ADJUSTMENT_VIEW = "hasAuthority('" + INVENTORY_ADJUSTMENT_VIEW + "')";
    String HAS_INVENTORY_ADJUSTMENT_CREATE = "hasAuthority('" + INVENTORY_ADJUSTMENT_CREATE + "')";
    String HAS_INVENTORY_ADJUSTMENT_POST = "hasAuthority('" + INVENTORY_ADJUSTMENT_POST + "')";
    String HAS_INVENTORY_ADJUSTMENT_CANCEL = "hasAuthority('" + INVENTORY_ADJUSTMENT_CANCEL + "')";
    String HAS_INVENTORY_CHECK_VIEW = "hasAuthority('" + INVENTORY_CHECK_VIEW + "')";
    String HAS_INVENTORY_CHECK_CREATE = "hasAuthority('" + INVENTORY_CHECK_CREATE + "')";
    String HAS_INVENTORY_CHECK_ADJUST = "hasAuthority('" + INVENTORY_CHECK_ADJUST + "')";
    String HAS_INVENTORY_CHECK_CANCEL = "hasAuthority('" + INVENTORY_CHECK_CANCEL + "')";
    String HAS_INVENTORY_ALERT_VIEW = "hasAuthority('" + INVENTORY_ALERT_VIEW + "')";
    String HAS_INVENTORY_ALERT_CREATE = "hasAuthority('" + INVENTORY_ALERT_CREATE + "')";
    String HAS_INVENTORY_ALERT_HANDLE = "hasAuthority('" + INVENTORY_ALERT_HANDLE + "')";
    String HAS_INVENTORY_TRANSFER_VIEW = "hasAuthority('" + INVENTORY_TRANSFER_VIEW + "')";
    String HAS_INVENTORY_TRANSFER_CREATE = "hasAuthority('" + INVENTORY_TRANSFER_CREATE + "')";
    String HAS_INVENTORY_TRANSFER_POST = "hasAuthority('" + INVENTORY_TRANSFER_POST + "')";
    String HAS_INVENTORY_TRANSFER_CANCEL = "hasAuthority('" + INVENTORY_TRANSFER_CANCEL + "')";
    String HAS_INVENTORY_RESERVATION_VIEW = "hasAuthority('" + INVENTORY_RESERVATION_VIEW + "')";
    String HAS_INVENTORY_RESERVATION_CHECK = "hasAuthority('" + INVENTORY_RESERVATION_CHECK + "')";
    String HAS_INVENTORY_RESERVATION_RELEASE = "hasAuthority('" + INVENTORY_RESERVATION_RELEASE + "')";
    String HAS_INVENTORY_REPLENISHMENT_VIEW = "hasAuthority('" + INVENTORY_REPLENISHMENT_VIEW + "')";
    String HAS_INVENTORY_REPLENISHMENT_CREATE = "hasAuthority('" + INVENTORY_REPLENISHMENT_CREATE + "')";
    String HAS_INVENTORY_REPLENISHMENT_UPDATE = "hasAuthority('" + INVENTORY_REPLENISHMENT_UPDATE + "')";
    String HAS_INVENTORY_REPLENISHMENT_CANCEL = "hasAuthority('" + INVENTORY_REPLENISHMENT_CANCEL + "')";
    String HAS_INVENTORY_REPLENISHMENT_CONVERT = "hasAuthority('" + INVENTORY_REPLENISHMENT_CONVERT + "')";
    String HAS_INVENTORY_MRP_VIEW = "hasAuthority('" + INVENTORY_MRP_VIEW + "')";
    String HAS_INVENTORY_MRP_RUN = "hasAuthority('" + INVENTORY_MRP_RUN + "')";
    String HAS_INVENTORY_MRP_CONVERT = "hasAuthority('" + INVENTORY_MRP_CONVERT + "')";
    String HAS_INVENTORY_LOT_GENEALOGY = "hasAuthority('" + INVENTORY_LOT_GENEALOGY + "')";
    String HAS_INVENTORY_SERIAL_VIEW = "hasAuthority('" + INVENTORY_SERIAL_VIEW + "')";
    String HAS_INVENTORY_SERIAL_MANAGE = "hasAuthority('" + INVENTORY_SERIAL_MANAGE + "')";
}
