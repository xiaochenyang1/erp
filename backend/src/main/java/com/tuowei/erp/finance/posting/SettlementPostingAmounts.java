package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;

import java.math.BigDecimal;

/**
 * 收付款过账的本位币口径：核销冲减额、预收预付额与已实现汇兑损益。
 *
 * <p>三个金额都已折算到本位币并按记账精度取整，资金腿恒等于三者之和，凭证据此天然平衡。
 *
 * <ul>
 *   <li>{@code reliefAmount}：按应收/应付**入账汇率**折算的冲减额，保证全额核销时子账本位币余额归零；</li>
 *   <li>{@code advanceAmount}：收付款总额超出核销额的部分，按**结算汇率**折算，不产生汇兑损益；</li>
 *   <li>{@code fxAmount}：带符号的已实现汇兑损益 = 按结算汇率折算的核销额 − {@code reliefAmount}。</li>
 * </ul>
 */
public record SettlementPostingAmounts(
        BigDecimal reliefAmount,
        BigDecimal advanceAmount,
        BigDecimal fxAmount
) {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final BigDecimal MAX_ALIGNMENT = new BigDecimal("0.05");

    public SettlementPostingAmounts {
        reliefAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(reliefAmount));
        advanceAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(advanceAmount));
        fxAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(fxAmount));
    }

    /** 本位币单据（或多币种改造前的调用方）：没有汇率差，汇兑损益恒为 0。 */
    public static SettlementPostingAmounts withoutExchangeDifference(BigDecimal reliefAmount, BigDecimal advanceAmount) {
        return new SettlementPostingAmounts(reliefAmount, advanceAmount, ZERO_AMOUNT);
    }

    /**
     * 由核销明细汇总组装。
     *
     * <p>资金腿由各笔核销的本位币金额汇总推出，而不是拿单据总额乘一次汇率：这样凭证的每条腿都能
     * 逐分对回核销明细行。代价是多笔核销且汇率带小数时，资金腿可能与单据自身的
     * {@code base_amount} 差几分——那是分摊取整的固有误差，凭证内部始终借贷相等。
     *
     * @param allocatedBaseAmount 各笔核销按**结算汇率**折算的本位币金额之和
     * @param reliefAmount        各笔核销按**子账入账汇率**折算的本位币冲减额之和
     * @param advanceAmount       预收/预付的本位币金额
     */
    public static SettlementPostingAmounts fromAllocations(
            BigDecimal allocatedBaseAmount,
            BigDecimal reliefAmount,
            BigDecimal advanceAmount
    ) {
        BigDecimal relief = ScalePrecision.amount(ScalePrecision.zeroDefault(reliefAmount));
        BigDecimal allocatedBase = ScalePrecision.amount(ScalePrecision.zeroDefault(allocatedBaseAmount));
        return new SettlementPostingAmounts(relief, advanceAmount, allocatedBase.subtract(relief));
    }

    /**
     * Folds a sub-cent allocation remainder into the FX leg so the cash leg equals the
     * document base amount. Differences larger than five cents are left untouched.
     */
    public SettlementPostingAmounts alignCashTo(BigDecimal documentBaseAmount) {
        if (documentBaseAmount == null || documentBaseAmount.signum() == 0) {
            return this;
        }
        BigDecimal target = ScalePrecision.amount(documentBaseAmount);
        BigDecimal delta = ScalePrecision.amount(target.subtract(cashAmount()));
        if (delta.signum() == 0 || delta.abs().compareTo(MAX_ALIGNMENT) > 0) {
            return this;
        }
        return new SettlementPostingAmounts(reliefAmount, advanceAmount, fxAmount.add(delta));
    }

    /** 收付款总额超出核销额的部分构成预收/预付，凭证必须把它单独列腿才能借贷平衡；按结算汇率折算。 */
    public static BigDecimal advanceBaseAmount(BigDecimal totalAmount, BigDecimal allocatedAmount, BigDecimal exchangeRate) {
        BigDecimal advance = ScalePrecision.amount(
                ScalePrecision.zeroDefault(totalAmount).subtract(ScalePrecision.zeroDefault(allocatedAmount))
        );
        if (advance.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_AMOUNT;
        }
        return CurrencyAmountSupport.posting(advance, exchangeRate);
    }

    /** 资金腿金额，等于三条业务腿之和。 */
    public BigDecimal cashAmount() {
        return ScalePrecision.amount(reliefAmount.add(advanceAmount).add(fxAmount));
    }}
