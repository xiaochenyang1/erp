package com.tuowei.erp.finance.statement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.posting.SettlementPostingAmounts;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reconciles cash movements to the historical carrying amounts relieved by their allocations. */
@Service
public class PartnerStatementSettlementService {
    private static final int QUERY_BATCH_SIZE = 500;

    private final ReceiptAllocationMapper receiptAllocationMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;

    public PartnerStatementSettlementService(
            ReceiptAllocationMapper receiptAllocationMapper,
            PaymentAllocationMapper paymentAllocationMapper
    ) {
        this.receiptAllocationMapper = receiptAllocationMapper;
        this.paymentAllocationMapper = paymentAllocationMapper;
    }

    @Transactional(readOnly = true)
    public Map<Long, Adjustment> receipts(List<ReceiptEntity> receipts, List<ReceivableEntity> receivables, AuditMetadata audit) {
        if (receipts.isEmpty()) return Map.of();
        List<Settlement> settlements = receipts.stream().map(item -> new Settlement(
                item.getId(), item.getReceiptDate(), item.getReceiptNo(), item.getAmount(), item.getAllocatedAmount(),
                item.getExchangeRate(), item.getBaseAmount())).toList();
        Map<Long, BigDecimal> bookingRates = receivables.stream().collect(Collectors.toMap(
                ReceivableEntity::getId, item -> CurrencyAmountSupport.rate(item.getExchangeRate())));
        List<Long> ids = receipts.stream().map(ReceiptEntity::getId).toList();
        List<Allocation> allocations = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += QUERY_BATCH_SIZE) {
            receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
                            .eq(ReceiptAllocationEntity::getCompanyId, audit.companyId())
                            .eq(ReceiptAllocationEntity::getAccountBookId, audit.accountBookId())
                            .in(ReceiptAllocationEntity::getReceiptId, ids.subList(offset, Math.min(ids.size(), offset + QUERY_BATCH_SIZE))))
                    .forEach(item -> allocations.add(new Allocation(item.getReceiptId(), item.getReceivableId(),
                            item.getAmount(), item.getBaseAmount(), item.getBaseSettledAmount(), item.getFxGainLossAmount(),
                            item.getPostedBaseAmount(), item.getPostedBaseSettledAmount(), item.getRoundingAmount())));
        }
        return assemble(settlements, allocations, bookingRates);
    }

    @Transactional(readOnly = true)
    public Map<Long, Adjustment> payments(List<PaymentEntity> payments, List<PayableEntity> payables, AuditMetadata audit) {
        if (payments.isEmpty()) return Map.of();
        List<Settlement> settlements = payments.stream().map(item -> new Settlement(
                item.getId(), item.getPaymentDate(), item.getPaymentNo(), item.getAmount(), item.getAllocatedAmount(),
                item.getExchangeRate(), item.getBaseAmount())).toList();
        Map<Long, BigDecimal> bookingRates = payables.stream().collect(Collectors.toMap(
                PayableEntity::getId, item -> CurrencyAmountSupport.rate(item.getExchangeRate())));
        List<Long> ids = payments.stream().map(PaymentEntity::getId).toList();
        List<Allocation> allocations = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += QUERY_BATCH_SIZE) {
            paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
                            .eq(PaymentAllocationEntity::getCompanyId, audit.companyId())
                            .eq(PaymentAllocationEntity::getAccountBookId, audit.accountBookId())
                            .in(PaymentAllocationEntity::getPaymentId, ids.subList(offset, Math.min(ids.size(), offset + QUERY_BATCH_SIZE))))
                    .forEach(item -> allocations.add(new Allocation(item.getPaymentId(), item.getPayableId(),
                            item.getAmount(), item.getBaseAmount(), item.getBaseSettledAmount(), item.getFxGainLossAmount(),
                            item.getPostedBaseAmount(), item.getPostedBaseSettledAmount(), item.getRoundingAmount())));
        }
        return assemble(settlements, allocations, bookingRates);
    }

    private Map<Long, Adjustment> assemble(
            List<Settlement> settlements,
            List<Allocation> allocations,
            Map<Long, BigDecimal> bookingRates
    ) {
        Map<Long, List<Allocation>> bySettlement = allocations.stream()
                .collect(Collectors.groupingBy(Allocation::settlementId));
        Map<Long, BigDecimal> cumulativeRelief = new HashMap<>();
        Map<Long, Adjustment> result = new HashMap<>();
        List<Settlement> ordered = settlements.stream().sorted(Comparator.comparing(Settlement::date)
                .thenComparing(Settlement::number, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Settlement::id)).toList();
        for (Settlement settlement : ordered) {
            BigDecimal originalAllocated = BigDecimal.ZERO;
            BigDecimal rawFx = BigDecimal.ZERO;
            BigDecimal storedRounding = BigDecimal.ZERO;
            boolean allPosted = true;
            Map<Long, BigDecimal> reliefByItem = new HashMap<>();
            for (Allocation allocation : bySettlement.getOrDefault(settlement.id(), List.of())) {
                BigDecimal bookingRate = bookingRates.get(allocation.itemId());
                if (bookingRate == null) {
                    throw new IllegalStateException("核销来源不属于当前往来单位，请核对收付款记录");
                }
                BigDecimal allocatedBase = snapshotOrConversion(allocation.baseAmount(), allocation.amount(), settlement.rate());
                boolean posted = allocation.postedCash() != null;
                if (posted && (allocation.postedRelief() == null || allocation.rounding() == null)) {
                    throw new IllegalStateException("核销过账快照不完整，请核对收付款记录");
                }
                allPosted &= posted;
                BigDecimal bookedBase = posted ? allocation.postedRelief()
                        : snapshotOrConversion(allocation.baseSettledAmount(), allocation.amount(), bookingRate);
                originalAllocated = originalAllocated.add(CurrencyAmountSupport.zero(allocation.amount()));
                reliefByItem.merge(allocation.itemId(), bookedBase, BigDecimal::add);
                // V161/V165 added zero-default columns to historical allocations. Reconstruct those
                // rows from the immutable document rates; an explicit nonzero FX snapshot wins.
                BigDecimal fx = posted ? allocation.postedCash().subtract(bookedBase).subtract(allocation.rounding()) : allocation.fxAmount();
                if (!posted && (fx == null || (fx.signum() == 0 && !allocation.hasBaseSnapshots()))) {
                    fx = allocatedBase.subtract(bookedBase);
                }
                rawFx = rawFx.add(fx);
                if (posted) storedRounding = storedRounding.add(allocation.rounding());
            }
            if (ScalePrecision.amount(originalAllocated).compareTo(
                    ScalePrecision.amount(CurrencyAmountSupport.zero(settlement.allocatedAmount()))) != 0) {
                throw new IllegalStateException("核销明细与收付款金额不一致，请先核对收付款记录");
            }

            BigDecimal relief = BigDecimal.ZERO;
            for (var entry : reliefByItem.entrySet()) {
                BigDecimal before = cumulativeRelief.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                BigDecimal after = before.add(entry.getValue());
                // Round cumulative relief per open item, then take the movement. Repeated partial
                // settlements must not each round the same fractional cent in the same direction.
                relief = relief.add(ScalePrecision.amount(after).subtract(ScalePrecision.amount(before)));
                cumulativeRelief.put(entry.getKey(), after);
            }
            BigDecimal cash = CurrencyAmountSupport.baseAmountOrFallback(
                    settlement.baseAmount(), settlement.amount(), settlement.rate());
            BigDecimal advance = SettlementPostingAmounts.advanceBaseAmount(
                    settlement.amount(), settlement.allocatedAmount(), settlement.rate());
            BigDecimal fx = ScalePrecision.amount(rawFx);
            BigDecimal rounding = ScalePrecision.amount(cash.subtract(relief).subtract(advance).subtract(fx));
            if (allPosted && rounding.compareTo(storedRounding) != 0) {
                throw new IllegalStateException("核销过账快照与收付款金额不一致，请核对收付款记录");
            }
            result.put(settlement.id(), new Adjustment(fx, rounding));
        }
        return result;
    }

    private BigDecimal snapshotOrConversion(BigDecimal stored, BigDecimal original, BigDecimal rate) {
        return stored != null && stored.signum() != 0 ? stored : CurrencyAmountSupport.base(original, rate);
    }

    /** Signed changes to the partner balance; the sign is independent of the cash movement direction. */
    public record Adjustment(BigDecimal fxAmount, BigDecimal roundingAmount) {
    }

    private record Settlement(Long id, LocalDate date, String number, BigDecimal amount,
                              BigDecimal allocatedAmount, BigDecimal rate, BigDecimal baseAmount) {
    }

    private record Allocation(Long settlementId, Long itemId, BigDecimal amount, BigDecimal baseAmount,
                              BigDecimal baseSettledAmount, BigDecimal fxAmount, BigDecimal postedCash,
                              BigDecimal postedRelief, BigDecimal rounding) {
        private boolean hasBaseSnapshots() {
            return baseAmount != null && baseAmount.signum() != 0
                    && baseSettledAmount != null && baseSettledAmount.signum() != 0;
        }
    }
}
