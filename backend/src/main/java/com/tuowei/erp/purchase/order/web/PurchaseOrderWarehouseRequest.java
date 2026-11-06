package com.tuowei.erp.purchase.order.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseOrderWarehouseRequest(@NotNull @Positive Long plannedWarehouseId) { }
