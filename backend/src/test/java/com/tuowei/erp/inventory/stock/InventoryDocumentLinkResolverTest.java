package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDocumentLinkResolverTest {

    private final InventoryDocumentLinkResolver resolver = new InventoryDocumentLinkResolver();

    @Test
    void resolvesRoutesForEveryTraversedBizType() {
        assertThat(resolver.resolveRoute("PURCHASE_RECEIPT", "PR-1")).isEqualTo("/purchase/receipts?keyword=PR-1");
        assertThat(resolver.resolveRoute("PURCHASE_RETURN", "PRT-1")).isEqualTo("/purchase/returns?keyword=PRT-1");
        assertThat(resolver.resolveRoute("SALES_DELIVERY", "SD-1")).isEqualTo("/sales/deliveries?keyword=SD-1");
        assertThat(resolver.resolveRoute("SALES_RETURN", "SR-1")).isEqualTo("/sales/returns?keyword=SR-1");
        assertThat(resolver.resolveRoute("PRODUCTION_ISSUE", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_COMPLETION", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_COMPLETION_REVERSAL", "MO-1"))
                .isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_RETURN", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("INVENTORY_ADJUSTMENT", "IA-1"))
                .isEqualTo("/inventory/adjustments?keyword=IA-1");
        assertThat(resolver.resolveRoute("INVENTORY_TRANSFER", "IT-1")).isEqualTo("/inventory/transfers?keyword=IT-1");
        assertThat(resolver.resolveRoute("INVENTORY_CHECK", "IC-1")).isEqualTo("/inventory/checks?keyword=IC-1");
        assertThat(resolver.resolveRoute("OPENING_INVENTORY", "OB-1"))
                .isEqualTo("/system/imports?importType=OPENING_INVENTORY&keyword=OB-1");
        assertThat(resolver.resolveRoute("OPENING_BALANCE", "OB-2"))
                .isEqualTo("/system/imports?importType=OPENING_INVENTORY&keyword=OB-2");
    }

    @Test
    void urlEncodesDocumentNumbersAndNormalizesCase() {
        assertThat(resolver.resolveRoute("purchase_receipt", " PR 1 ")).isEqualTo("/purchase/receipts?keyword=PR+1");
    }

    @Test
    void returnsNullRouteForBlankOrUnknownInput() {
        assertThat(resolver.resolveRoute(null, "X")).isNull();
        assertThat(resolver.resolveRoute("PURCHASE_RECEIPT", null)).isNull();
        assertThat(resolver.resolveRoute("PURCHASE_RECEIPT", " ")).isNull();
        assertThat(resolver.resolveRoute("MYSTERY_TYPE", "X-1")).isNull();
    }

    @Test
    void labelsFallBackToRawBizType() {
        assertThat(resolver.resolveLabel("PURCHASE_RECEIPT")).isEqualTo("采购收货");
        assertThat(resolver.resolveLabel("PURCHASE_RETURN")).isEqualTo("采购退货");
        assertThat(resolver.resolveLabel("SALES_DELIVERY")).isEqualTo("销售发货");
        assertThat(resolver.resolveLabel("SALES_RETURN")).isEqualTo("销售退货");
        assertThat(resolver.resolveLabel("PRODUCTION_ISSUE")).isEqualTo("生产领料");
        assertThat(resolver.resolveLabel("PRODUCTION_COMPLETION")).isEqualTo("生产完工");
        assertThat(resolver.resolveLabel("PRODUCTION_COMPLETION_REVERSAL")).isEqualTo("完工红冲");
        assertThat(resolver.resolveLabel("PRODUCTION_RETURN")).isEqualTo("生产退料");
        assertThat(resolver.resolveLabel("INVENTORY_ADJUSTMENT")).isEqualTo("库存调整");
        assertThat(resolver.resolveLabel("INVENTORY_TRANSFER")).isEqualTo("库存调拨");
        assertThat(resolver.resolveLabel("INVENTORY_CHECK")).isEqualTo("库存盘点");
        assertThat(resolver.resolveLabel("OPENING_INVENTORY")).isEqualTo("期初库存");
        assertThat(resolver.resolveLabel("OPENING_BALANCE")).isEqualTo("期初库存");
        assertThat(resolver.resolveLabel("MYSTERY_TYPE")).isEqualTo("MYSTERY_TYPE");
        assertThat(resolver.resolveLabel(null)).isNull();
        assertThat(resolver.resolveLabel(" ")).isNull();
    }
}
