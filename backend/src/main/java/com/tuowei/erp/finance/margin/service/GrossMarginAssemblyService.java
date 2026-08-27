package com.tuowei.erp.finance.margin.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.margin.web.GrossMarginLineResponse;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure gross-margin calculations and DTO assembly. */
@Service
public class GrossMarginAssemblyService {

    public GrossMarginSummaryResponse assemble(GrossMarginQueryService.GrossMarginData data) {
        List<GrossMarginLineResponse> lines = new ArrayList<>();
        BigDecimal salesTotal = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        for (Map<String, Object> row : data.rows()) {
            BigDecimal salesQty = toBd(row.get("salesQty"));
            BigDecimal salesAmount = ScalePrecision.amount(toBd(row.get("salesAmount")));
            BigDecimal costAmount = ScalePrecision.amount(toBd(row.get("costAmount")));
            BigDecimal margin = ScalePrecision.amount(salesAmount.subtract(costAmount));
            BigDecimal rate = rate(margin, salesAmount);
            salesTotal = salesTotal.add(salesAmount);
            costTotal = costTotal.add(costAmount);
            Long productId = row.get("productId") == null ? null : ((Number) row.get("productId")).longValue();
            lines.add(new GrossMarginLineResponse(
                    productId,
                    str(row.get("productCode")),
                    str(row.get("productName")),
                    ScalePrecision.quantity(salesQty),
                    salesAmount,
                    costAmount,
                    margin,
                    rate
            ));
        }
        salesTotal = ScalePrecision.amount(salesTotal);
        costTotal = ScalePrecision.amount(costTotal);
        BigDecimal gross = ScalePrecision.amount(salesTotal.subtract(costTotal));
        return new GrossMarginSummaryResponse(
                data.dateFrom(),
                data.dateTo(),
                salesTotal,
                costTotal,
                gross,
                rate(gross, salesTotal),
                lines
        );
    }

    private BigDecimal rate(BigDecimal margin, BigDecimal salesAmount) {
        return salesAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : margin.multiply(new BigDecimal("100")).divide(salesAmount, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBd(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }
}
