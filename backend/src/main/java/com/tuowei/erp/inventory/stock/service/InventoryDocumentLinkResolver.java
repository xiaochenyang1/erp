package com.tuowei.erp.inventory.stock.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Maps an {@code inv_txn.biz_type} to the frontend route and display label of its source document.
 *
 * <p>Extracted from {@link InventoryLotQueryService} so that lot trace and lot genealogy share one
 * mapping instead of drifting apart. The label values are Chinese; callers that need a localized
 * label should map {@code bizType} through i18n themselves and treat this as a fallback.
 */
@Component
public class InventoryDocumentLinkResolver {

    public String resolveRoute(String bizType, String bizNo) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizNo)) {
            return null;
        }
        String type = bizType.trim().toUpperCase(Locale.ROOT);
        String encoded = URLEncoder.encode(bizNo.trim(), StandardCharsets.UTF_8);
        return switch (type) {
            case "PURCHASE_RECEIPT" -> "/purchase/receipts?keyword=" + encoded;
            case "PURCHASE_RETURN" -> "/purchase/returns?keyword=" + encoded;
            case "SALES_DELIVERY" -> "/sales/deliveries?keyword=" + encoded;
            case "SALES_RETURN" -> "/sales/returns?keyword=" + encoded;
            case "PRODUCTION_ISSUE", "PRODUCTION_COMPLETION", "PRODUCTION_COMPLETION_REVERSAL", "PRODUCTION_RETURN" ->
                    "/production/orders?keyword=" + encoded;
            case "INVENTORY_ADJUSTMENT" -> "/inventory/adjustments?keyword=" + encoded;
            case "INVENTORY_TRANSFER" -> "/inventory/transfers?keyword=" + encoded;
            case "INVENTORY_CHECK" -> "/inventory/checks?keyword=" + encoded;
            case "OPENING_INVENTORY", "OPENING_BALANCE" ->
                    "/system/imports?importType=OPENING_INVENTORY&keyword=" + encoded;
            default -> null;
        };
    }

    public String resolveLabel(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        return switch (bizType.trim().toUpperCase(Locale.ROOT)) {
            case "PURCHASE_RECEIPT" -> "采购收货";
            case "PURCHASE_RETURN" -> "采购退货";
            case "SALES_DELIVERY" -> "销售发货";
            case "SALES_RETURN" -> "销售退货";
            case "PRODUCTION_ISSUE" -> "生产领料";
            case "PRODUCTION_COMPLETION" -> "生产完工";
            case "PRODUCTION_COMPLETION_REVERSAL" -> "完工红冲";
            case "PRODUCTION_RETURN" -> "生产退料";
            case "INVENTORY_ADJUSTMENT" -> "库存调整";
            case "INVENTORY_TRANSFER" -> "库存调拨";
            case "INVENTORY_CHECK" -> "库存盘点";
            case "OPENING_INVENTORY", "OPENING_BALANCE" -> "期初库存";
            default -> bizType;
        };
    }
}
