package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure reconciliation calculations and response assembly. */
@Service
public class InventoryFinanceReconciliationAssemblyService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    public InventoryFinanceReconciliationResponse assembleSummary(
            InventoryFinanceReconciliationQueryService.SummaryData data
    ) {
        BigDecimal differenceAmount = ScalePrecision.amount(data.inventoryAmount().subtract(data.financeAmount()));
        return new InventoryFinanceReconciliationResponse(
                data.period().getId(),
                data.period().getPeriodMonth(),
                data.inventoryAmount(),
                data.financeAmount(),
                differenceAmount,
                differenceAmount.compareTo(BigDecimal.ZERO) == 0
        );
    }

    public List<InventoryFinanceDifferenceResponse> assembleDifferences(
            InventoryFinanceReconciliationQueryService.DifferenceData data,
            InventoryFinanceDifferenceQuery query
    ) {
        Map<String, DifferenceAccumulator> rows = new LinkedHashMap<>();
        for (InventoryFinanceReconciliationQueryService.InventorySourceAmount amount : data.inventorySources()) {
            rows.computeIfAbsent(
                    amount.sourceKey(),
                    ignored -> new DifferenceAccumulator(amount.sourceType(), amount.sourceNo())
            ).addInventory(amount.amount());
        }
        for (InventoryFinanceReconciliationQueryService.FinanceSourceAmount amount : data.financeSources()) {
            rows.computeIfAbsent(
                    amount.sourceKey(),
                    ignored -> new DifferenceAccumulator(amount.sourceType(), amount.sourceNo())
            ).addFinance(amount.amount());
        }
        String filterType = normalizeType(query == null ? null : query.getDifferenceType());
        return rows.entrySet().stream()
                .map(entry -> toDifferenceResponse(entry.getKey(), entry.getValue()))
                .filter(response -> response.differenceAmount().compareTo(BigDecimal.ZERO) != 0)
                .filter(response -> filterType == null || filterType.equals(response.differenceType()))
                .toList();
    }

    public InventoryFinanceDifferenceDetailResponse assembleDetail(
            InventoryFinanceReconciliationQueryService.DifferenceDetailData data
    ) {
        BigDecimal inventoryAmount = data.inventoryTransactions().stream()
                .map(this::signedInventoryAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal financeAmount = data.voucherEntries().stream()
                .map(entry -> ScalePrecision.zeroDefault(entry.debitAmount())
                        .subtract(ScalePrecision.zeroDefault(entry.creditAmount())))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        inventoryAmount = ScalePrecision.amount(inventoryAmount);
        financeAmount = ScalePrecision.amount(financeAmount);
        BigDecimal differenceAmount = ScalePrecision.amount(inventoryAmount.subtract(financeAmount));
        return new InventoryFinanceDifferenceDetailResponse(
                data.period().getId(),
                data.period().getPeriodMonth(),
                sourceKey(data.sourceType(), data.sourceNo()),
                data.sourceType(),
                data.sourceNo(),
                inventoryAmount,
                financeAmount,
                differenceAmount,
                resolveDifferenceType(inventoryAmount, financeAmount),
                data.inventoryTransactions(),
                data.voucherEntries()
        );
    }

    private BigDecimal signedInventoryAmount(
            InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse transaction
    ) {
        BigDecimal amount = ScalePrecision.zeroDefault(transaction.amount());
        return "IN".equals(transaction.direction()) ? amount : amount.negate();
    }

    private InventoryFinanceDifferenceResponse toDifferenceResponse(
            String sourceKey,
            DifferenceAccumulator accumulator
    ) {
        BigDecimal inventoryAmount = ScalePrecision.amount(accumulator.inventoryAmount());
        BigDecimal financeAmount = ScalePrecision.amount(accumulator.financeAmount());
        BigDecimal differenceAmount = ScalePrecision.amount(inventoryAmount.subtract(financeAmount));
        return new InventoryFinanceDifferenceResponse(
                sourceKey,
                accumulator.sourceType(),
                accumulator.sourceNo(),
                inventoryAmount,
                financeAmount,
                differenceAmount,
                resolveDifferenceType(inventoryAmount, financeAmount)
        );
    }

    private String resolveDifferenceType(BigDecimal inventoryAmount, BigDecimal financeAmount) {
        if (inventoryAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "FINANCE_ONLY";
        }
        if (financeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "INVENTORY_ONLY";
        }
        return "AMOUNT_MISMATCH";
    }

    private String normalizeType(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String sourceKey(String sourceType, String sourceNo) {
        return sourceType + ":" + sourceNo;
    }

    private static final class DifferenceAccumulator {
        private final String sourceType;
        private final String sourceNo;
        private BigDecimal inventoryAmount = ZERO_AMOUNT;
        private BigDecimal financeAmount = ZERO_AMOUNT;

        private DifferenceAccumulator(String sourceType, String sourceNo) {
            this.sourceType = sourceType;
            this.sourceNo = sourceNo;
        }

        private void addInventory(BigDecimal amount) {
            inventoryAmount = ScalePrecision.amount(inventoryAmount.add(ScalePrecision.zeroDefault(amount)));
        }

        private void addFinance(BigDecimal amount) {
            financeAmount = ScalePrecision.amount(financeAmount.add(ScalePrecision.zeroDefault(amount)));
        }

        private String sourceType() {
            return sourceType;
        }

        private String sourceNo() {
            return sourceNo;
        }

        private BigDecimal inventoryAmount() {
            return inventoryAmount;
        }

        private BigDecimal financeAmount() {
            return financeAmount;
        }
    }
}
