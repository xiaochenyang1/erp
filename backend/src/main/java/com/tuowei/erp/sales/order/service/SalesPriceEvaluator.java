package com.tuowei.erp.sales.order.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.price.service.SalesPriceService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售最低价校验。创建/编辑/提交销售订单时，若存在生效价目则单价不得低于最低价。
 *
 * <p>取价优先级由 {@link SalesPriceService} 决定：客户专价 &gt; 商品通用价；无匹配价目时不拦截。
 */
@Component
public class SalesPriceEvaluator {

    private final SalesPriceService salesPriceService;

    public SalesPriceEvaluator(SalesPriceService salesPriceService) {
        this.salesPriceService = salesPriceService;
    }

    public void assertLinesWithinMinPrice(
            Long companyId,
            Long accountBookId,
            Long customerId,
            LocalDate orderDate,
            List<SalesOrderLineRequest> lines
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        LocalDate bizDate = orderDate == null ? LocalDate.now() : orderDate;
        int index = 0;
        for (SalesOrderLineRequest line : lines) {
            index++;
            if (line == null || line.productId() == null || line.price() == null) {
                continue;
            }
            BigDecimal minPrice = salesPriceService.resolveMinPrice(
                    companyId,
                    accountBookId,
                    customerId,
                    line.productId(),
                    bizDate
            );
            if (minPrice == null) {
                continue;
            }
            BigDecimal price = ScalePrecision.amount(line.price());
            if (price.compareTo(minPrice) < 0) {
                throw new IllegalArgumentException(String.format(
                        "第 %d 行单价 %s 低于生效最低价 %s，请调整价格",
                        index,
                        price.toPlainString(),
                        minPrice.toPlainString()
                ));
            }
        }
    }
}
