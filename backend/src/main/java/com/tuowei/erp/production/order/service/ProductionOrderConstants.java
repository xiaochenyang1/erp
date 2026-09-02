package com.tuowei.erp.production.order.service;

/** Shared production-order statuses and inventory source type. */
public final class ProductionOrderConstants {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_MATERIAL_ISSUED = "MATERIAL_ISSUED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String SOURCE_TYPE = "PRODUCTION_ORDER";

    private ProductionOrderConstants() {
    }
}
