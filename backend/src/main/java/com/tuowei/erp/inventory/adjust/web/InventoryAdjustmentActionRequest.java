package com.tuowei.erp.inventory.adjust.web;

import jakarta.validation.constraints.Size;

public record InventoryAdjustmentActionRequest(@Size(max = 255) String comment) {
}
