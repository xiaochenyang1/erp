package com.tuowei.erp.finance.fx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.fx.mapper.FxRevaluationBalanceMapper;
import com.tuowei.erp.finance.fx.model.FxRevaluationBalance;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One period-end calculation shared by revaluation posting and the month-end close check. */
@Service
public class FxRevaluationBasisService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FxRevaluationBalanceMapper balanceMapper;
    private final SettlementCurrencyService currencyService;
    private final BaseCurrencyService baseCurrencyService;

    public FxRevaluationBasisService(FxRevaluationBalanceMapper balanceMapper,
                                    SettlementCurrencyService currencyService, BaseCurrencyService baseCurrencyService) {
        this.balanceMapper = balanceMapper;
        this.currencyService = currencyService;
        this.baseCurrencyService = baseCurrencyService;
    }

    @Transactional(readOnly = true)
    public Valuation valuate(AccountPeriodEntity period, AuditMetadata audit) {
        if (!Objects.equals(period.getCompanyId(), audit.companyId())
                || !Objects.equals(period.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        if (period.getEndDate() == null) throw new IllegalArgumentException("会计期间结束日期不能为空");
        String baseCurrency = CurrencyAmountSupport.currency(baseCurrencyService.current(audit));
        var balances = balanceMapper.selectAtPeriodEnd(audit.companyId(), audit.accountBookId(), period.getEndDate(), baseCurrency)
                .stream().sorted(Comparator.comparing(FxRevaluationBalance::side).thenComparing(FxRevaluationBalance::itemId)).toList();
        Map<String, BigDecimal> rates = new HashMap<>();
        List<ItemBasis> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal ar = BigDecimal.ZERO;
        BigDecimal ap = BigDecimal.ZERO;
        for (FxRevaluationBalance item : balances) {
            String currency = CurrencyAmountSupport.currency(item.currencyCode());
            if (baseCurrency.equals(currency)) continue;
            BigDecimal original = CurrencyAmountSupport.zero(item.originalAmount());
            BigDecimal currentSettled = CurrencyAmountSupport.zero(item.settledAmount());
            BigDecimal futureSettled = CurrencyAmountSupport.zero(item.futureSettledAmount());
            BigDecimal settledAtEnd = ScalePrecision.amount(currentSettled.subtract(futureSettled));
            if (original.signum() < 0 || currentSettled.signum() < 0 || currentSettled.compareTo(original) > 0
                    || futureSettled.signum() < 0 || settledAtEnd.signum() < 0) {
                throw invalidBalance(item);
            }
            BigDecimal remaining = ScalePrecision.amount(original.subtract(settledAtEnd));
            if (remaining.signum() == 0) continue;

            // Subtract only active post-period settlements. This retains imported opening
            // settlements, which have no receipt/payment allocations of their own.
            // Cancellations reverse on the original business date, so cancelled headers are
            // excluded by the mapper regardless of their wall-clock cancellation timestamp.
            BigDecimal currentBaseSettled = item.postedBaseSettledAmount() != null ? item.postedBaseSettledAmount()
                    : currentSettled.signum() == 0 ? BigDecimal.ZERO
                    : snapshotOrConversion(item.baseSettledAmount(), currentSettled, item.exchangeRate());
            BigDecimal baseSettledAtEnd = currentBaseSettled.subtract(CurrencyAmountSupport.zero(item.futureBaseSettledAmount()));
            if (ScalePrecision.amount(baseSettledAtEnd).signum() < 0
                    || (settledAtEnd.signum() == 0 && ScalePrecision.amount(baseSettledAtEnd).signum() != 0)) {
                throw invalidBalance(item);
            }
            BigDecimal carrying = CurrencyAmountSupport.baseRemaining(original, settledAtEnd,
                    item.baseOriginalAmount(), baseSettledAtEnd, item.exchangeRate(),
                    item.postedBaseSettledAmount() != null ? baseSettledAtEnd : null);
            String direction = "DECREASE".equalsIgnoreCase(item.direction()) ? "DECREASE" : "INCREASE";
            if ("DECREASE".equals(direction)) {
                remaining = remaining.negate();
                carrying = carrying.negate();
            }
            BigDecimal rate = rates.computeIfAbsent(currency,
                    code -> currencyService.resolve(code, null, period.getEndDate(), audit).exchangeRate());
            BigDecimal revalued = ScalePrecision.amount(remaining.multiply(rate));
            BigDecimal difference = ScalePrecision.amount(revalued.subtract(carrying));
            total = total.add(remaining);
            if ("AR".equals(item.side())) ar = ar.add(difference);
            else ap = ap.add(difference);
            items.add(new ItemBasis(item.side(), item.itemId().toString(), Objects.toString(item.partnerId(), ""),
                    item.bizDate().toString(), direction, currency, decimal(remaining), decimal(carrying),
                    decimal(rate), decimal(revalued), decimal(difference)));
        }
        Basis basis = new Basis(1, audit.companyId().toString(), audit.accountBookId().toString(), period.getId().toString(),
                period.getEndDate().toString(), baseCurrency, List.copyOf(items));
        String json = serialize(basis);
        return new Valuation(ScalePrecision.amount(total), ScalePrecision.amount(ar), ScalePrecision.amount(ap),
                hash(json), json, items.size());
    }

    private static BigDecimal snapshotOrConversion(BigDecimal stored, BigDecimal original, BigDecimal rate) {
        return stored != null && stored.signum() != 0 ? stored : CurrencyAmountSupport.base(original, rate);
    }

    private static BusinessConflictException invalidBalance(FxRevaluationBalance item) {
        return new BusinessConflictException("无法还原期末核销余额，请核对应收应付及收付款记录：" + item.side() + " " + item.itemId());
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String serialize(Basis basis) {
        try {
            return JSON.writeValueAsString(basis);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("保存期末调汇计算依据失败", ex);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Valuation(BigDecimal openOriginalTotal, BigDecimal arAdjustment, BigDecimal apAdjustment,
                            String basisHash, String basisJson, int openItemCount) {
        public boolean hasAdjustment() { return arAdjustment.signum() != 0 || apAdjustment.signum() != 0; }
        public boolean hasExposure() { return openItemCount > 0; }
    }

    private record Basis(int version, String companyId, String accountBookId, String periodId,
                         String periodEnd, String baseCurrency, List<ItemBasis> items) {
    }

    private record ItemBasis(String side, String itemId, String partnerId, String bizDate, String direction,
                             String currency, String openOriginal, String carryingBase, String closingRate,
                             String revaluedBase, String adjustment) {
    }
}
