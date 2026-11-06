package com.tuowei.erp.masterdata.product.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Omit the whole object to retain a legacy client's settings; null warehouse inside it means demand warehouse. */
public record ProductMrpPolicy(
        @NotNull @Pattern(regexp = "AUTO|PURCHASE|PRODUCTION", message = "MRP供给策略必须为AUTO、PURCHASE或PRODUCTION") String supplyType,
        @Positive Long materialWarehouseId
) { }
