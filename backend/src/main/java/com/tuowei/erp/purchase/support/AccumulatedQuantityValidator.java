package com.tuowei.erp.purchase.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class AccumulatedQuantityValidator {

    private final Map<Long, BigDecimal> accumulatedQtyBySourceId = new HashMap<>();
    private final String exceedMessage;

    public AccumulatedQuantityValidator(String exceedMessage) {
        this.exceedMessage = exceedMessage;
    }

    public void ensureWithinLimit(Long sourceId, BigDecimal qty, BigDecimal limitQty) {
        ensureWithinLimit(sourceId, qty, ignored -> limitQty);
    }

    public void ensureWithinLimit(Long sourceId, BigDecimal qty, Function<Long, BigDecimal> limitQtyProvider) {
        BigDecimal normalizedQty = ScalePrecision.quantity(qty);
        BigDecimal accumulatedQty = accumulatedQtyBySourceId.getOrDefault(sourceId, BigDecimal.ZERO).add(normalizedQty);
        BigDecimal limitQty = ScalePrecision.quantity(limitQtyProvider.apply(sourceId));
        if (ScalePrecision.quantity(accumulatedQty).compareTo(limitQty) > 0) {
            throw new IllegalArgumentException(exceedMessage);
        }
        accumulatedQtyBySourceId.put(sourceId, ScalePrecision.quantity(accumulatedQty));
    }
}
