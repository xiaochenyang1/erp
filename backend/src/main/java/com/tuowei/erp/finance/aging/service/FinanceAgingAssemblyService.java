package com.tuowei.erp.finance.aging.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.aging.web.FinanceAgingBucketResponse;
import com.tuowei.erp.finance.aging.web.FinanceAgingCurrencyExposureResponse;
import com.tuowei.erp.finance.aging.web.FinanceAgingOpenItemResponse;
import com.tuowei.erp.finance.aging.web.FinanceAgingSummaryResponse;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure aging bucket calculation and response assembly. */
@Service
public class FinanceAgingAssemblyService {

    private static final int OVERDUE_TOP_N = 20;

    public FinanceAgingSummaryResponse assemble(FinanceAgingQueryService.AgingData data) {
        Map<String, BucketAgg> arBuckets = emptyBuckets();
        Map<String, BucketAgg> apBuckets = emptyBuckets();
        Map<String, CurrencyAgg> arCurrencies = new LinkedHashMap<>();
        Map<String, CurrencyAgg> apCurrencies = new LinkedHashMap<>();
        List<FinanceAgingOpenItemResponse> arItems = new ArrayList<>();
        List<FinanceAgingOpenItemResponse> apItems = new ArrayList<>();

        for (ReceivableEntity entity : data.receivables()) {
            BigDecimal remaining = remaining(entity.getOriginalAmount(), entity.getSettledAmount());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String currencyCode = CurrencyAmountSupport.currency(entity.getCurrencyCode());
            BigDecimal exchangeRate = CurrencyAmountSupport.rate(entity.getExchangeRate());
            BigDecimal baseRemaining = baseRemaining(remaining, exchangeRate);
            LocalDate dueDate = effectiveDueDate(entity.getDueDate(), entity.getBizDate());
            long days = agingDays(dueDate, data.asOfDate());
            String bucket = bucketCode(days);
            arBuckets.get(bucket).add(baseRemaining);
            arCurrencies.computeIfAbsent(currencyCode, CurrencyAgg::new).add(remaining, baseRemaining);
            arItems.add(new FinanceAgingOpenItemResponse(
                    "RECEIVABLE", entity.getId(), entity.getReceivableNo(), entity.getCustomerId(),
                    data.customerNames().get(entity.getCustomerId()), entity.getBizDate(), dueDate, days,
                    bucket, remaining, currencyCode, exchangeRate, baseRemaining, entity.getStatus()
            ));
        }

        for (PayableEntity entity : data.payables()) {
            BigDecimal remaining = remaining(entity.getOriginalAmount(), entity.getSettledAmount());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String currencyCode = CurrencyAmountSupport.currency(entity.getCurrencyCode());
            BigDecimal exchangeRate = CurrencyAmountSupport.rate(entity.getExchangeRate());
            BigDecimal baseRemaining = baseRemaining(remaining, exchangeRate);
            LocalDate dueDate = effectiveDueDate(entity.getDueDate(), entity.getBizDate());
            long days = agingDays(dueDate, data.asOfDate());
            String bucket = bucketCode(days);
            apBuckets.get(bucket).add(baseRemaining);
            apCurrencies.computeIfAbsent(currencyCode, CurrencyAgg::new).add(remaining, baseRemaining);
            apItems.add(new FinanceAgingOpenItemResponse(
                    "PAYABLE", entity.getId(), entity.getPayableNo(), entity.getSupplierId(),
                    data.supplierNames().get(entity.getSupplierId()), entity.getBizDate(), dueDate, days,
                    bucket, remaining, currencyCode, exchangeRate, baseRemaining, entity.getStatus()
            ));
        }

        // 逾期排序按本位币未结额，跨币种比较才有意义。
        Comparator<FinanceAgingOpenItemResponse> byDaysDesc =
                Comparator.comparingLong(FinanceAgingOpenItemResponse::agingDays).reversed()
                        .thenComparing(FinanceAgingOpenItemResponse::baseRemainingAmount, Comparator.reverseOrder());
        List<FinanceAgingOpenItemResponse> overdueAr = arItems.stream()
                .filter(item -> item.agingDays() > 0)
                .sorted(byDaysDesc)
                .limit(OVERDUE_TOP_N)
                .toList();
        List<FinanceAgingOpenItemResponse> overdueAp = apItems.stream()
                .filter(item -> item.agingDays() > 0)
                .sorted(byDaysDesc)
                .limit(OVERDUE_TOP_N)
                .toList();

        return new FinanceAgingSummaryResponse(
                data.asOfDate(),
                CurrencyAmountSupport.currency(data.baseCurrencyCode()),
                sumBuckets(arBuckets),
                sumBuckets(apBuckets),
                toBucketList(arBuckets),
                toBucketList(apBuckets),
                toCurrencyList(arCurrencies),
                toCurrencyList(apCurrencies),
                overdueAr,
                overdueAp
        );
    }

    /**
     * 本位币未结额按子账入账汇率折算，而不是读 {@code base_*} 列。
     *
     * <p>V161 之前的历史行 {@code base_original_amount}/{@code base_settled_amount} 是 0 默认值，
     * 直接相减会把未结额算成 0；按行自带汇率折算对历史行（汇率 1）与新行都成立。
     */
    private BigDecimal baseRemaining(BigDecimal remaining, BigDecimal exchangeRate) {
        return CurrencyAmountSupport.posting(remaining, exchangeRate);
    }

    private LocalDate effectiveDueDate(LocalDate dueDate, LocalDate bizDate) {
        return dueDate != null ? dueDate : bizDate;
    }

    private long agingDays(LocalDate dueDate, LocalDate asOf) {
        if (dueDate == null) {
            return 0L;
        }
        return Math.max(ChronoUnit.DAYS.between(dueDate, asOf), 0L);
    }

    private String bucketCode(long days) {
        if (days <= 30) {
            return "D0_30";
        }
        if (days <= 60) {
            return "D31_60";
        }
        if (days <= 90) {
            return "D61_90";
        }
        return "D90_PLUS";
    }

    private Map<String, BucketAgg> emptyBuckets() {
        Map<String, BucketAgg> buckets = new HashMap<>();
        buckets.put("D0_30", new BucketAgg("D0_30", "0-30 天", 0, 30));
        buckets.put("D31_60", new BucketAgg("D31_60", "31-60 天", 31, 60));
        buckets.put("D61_90", new BucketAgg("D61_90", "61-90 天", 61, 90));
        buckets.put("D90_PLUS", new BucketAgg("D90_PLUS", "90 天以上", 91, null));
        return buckets;
    }

    private List<FinanceAgingBucketResponse> toBucketList(Map<String, BucketAgg> buckets) {
        return List.of("D0_30", "D31_60", "D61_90", "D90_PLUS").stream()
                .map(buckets::get)
                .map(BucketAgg::toResponse)
                .toList();
    }

    private BigDecimal sumBuckets(Map<String, BucketAgg> buckets) {
        return buckets.values().stream()
                .map(BucketAgg::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<FinanceAgingCurrencyExposureResponse> toCurrencyList(Map<String, CurrencyAgg> currencies) {
        return currencies.values().stream()
                .sorted(Comparator.comparing(CurrencyAgg::currencyCode))
                .map(CurrencyAgg::toResponse)
                .toList();
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))
        );
    }

    private static final class CurrencyAgg {
        private final String currencyCode;
        private long count;
        private BigDecimal originalAmount = BigDecimal.ZERO;
        private BigDecimal baseAmount = BigDecimal.ZERO;

        private CurrencyAgg(String currencyCode) {
            this.currencyCode = currencyCode;
        }

        private String currencyCode() {
            return currencyCode;
        }

        private void add(BigDecimal original, BigDecimal base) {
            count++;
            originalAmount = ScalePrecision.amount(originalAmount.add(original));
            baseAmount = ScalePrecision.amount(baseAmount.add(base));
        }

        private FinanceAgingCurrencyExposureResponse toResponse() {
            return new FinanceAgingCurrencyExposureResponse(currencyCode, count, originalAmount, baseAmount);
        }
    }

    private static final class BucketAgg {
        private final String code;
        private final String label;
        private final int minDays;
        private final Integer maxDays;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;

        private BucketAgg(String code, String label, int minDays, Integer maxDays) {
            this.code = code;
            this.label = label;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        private void add(BigDecimal value) {
            count++;
            amount = ScalePrecision.amount(amount.add(value));
        }

        private BigDecimal amount() {
            return amount;
        }

        private FinanceAgingBucketResponse toResponse() {
            return new FinanceAgingBucketResponse(code, label, minDays, maxDays, count, amount);
        }
    }
}
