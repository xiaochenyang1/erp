package com.tuowei.erp.inventory.alert.web;

import jakarta.validation.constraints.NotNull;

/**
 * 库存预警处置请求。预警命中是按（仓库 + 商品）动态派生的，处置以该维度收敛。
 */
public record InventoryAlertHandleRequest(
        @NotNull(message = "仓库不能为空") Long warehouseId,
        @NotNull(message = "商品不能为空") Long productId,
        String remark
) {
}
