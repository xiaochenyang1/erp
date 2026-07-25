package com.tuowei.erp.purchase.order.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.price.service.PurchasePriceService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购最高价校验。创建/编辑/提交采购订单时，若存在生效价目则单价不得高于最高价。
 *
 * <p>取价优先级由 {@link PurchasePriceService} 决定：供应商专价 &gt; 商品通用价；无匹配价目时不拦截。
 */
@Component
public class PurchasePriceEvaluator {

    private final PurchasePriceService purchasePriceService;

    public PurchasePriceEvaluator(PurchasePriceService purchasePriceService) {
        this.purchasePriceService = purchasePriceService;
    }

    public void assertLinesWithinMaxPrice(
            Long companyId,
            Long accountBookId,
            Long supplierId,
            LocalDate orderDate,
            List<PurchaseOrderLineRequest> lines
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        LocalDate bizDate = orderDate == null ? LocalDate.now() : orderDate;
        int index = 0;
        for (PurchaseOrderLineRequest line : lines) {
            index++;
            if (line == null || line.productId() == null || line.price() == null) {
                continue;
            }
            BigDecimal maxPrice = purchasePriceService.resolveMaxPrice(
                    companyId,
                    accountBookId,
                    supplierId,
                    line.productId(),
                    bizDate
            );
            if (maxPrice == null) {
                continue;
            }
            BigDecimal price = ScalePrecision.amount(line.price());
            if (price.compareTo(maxPrice) > 0) {
                throw new IllegalArgumentException(String.format(
                        "第 %d 行单价 %s 高于生效最高价 %s，请调整价格",
                        index,
                        price.toPlainString(),
                        maxPrice.toPlainString()
                ));
            }
        }
    }
}
