package com.tuowei.erp.finance.payment.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.finance.posting.SettlementPostingAmounts;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

@Service
public class PaymentCommandService {
    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final BigDecimal ZERO_BASE_AMOUNT = CurrencyAmountSupport.base(BigDecimal.ZERO, BigDecimal.ONE);
    private final PaymentMapper paymentMapper; private final PaymentAllocationMapper paymentAllocationMapper; private final PayableMapper payableMapper; private final PaymentNumberService paymentNumberService; private final AuditMetadataFactory auditMetadataFactory; private final AccountPeriodGuard accountPeriodGuard; private final PaymentQueryService queryService; private final FinancePostingService financePostingService;
    private final BaseCurrencyService baseCurrencyService;
    private final SettlementCurrencyService settlementCurrencyService;
    public PaymentCommandService(PaymentMapper a, PaymentAllocationMapper b, PayableMapper c, PaymentNumberService d, AuditMetadataFactory e, AccountPeriodGuard f, PaymentQueryService g, FinancePostingService h) { this(a,b,c,d,e,f,g,h,null); }
    @Autowired
    public PaymentCommandService(PaymentMapper paymentMapper, PaymentAllocationMapper paymentAllocationMapper, PayableMapper payableMapper, PaymentNumberService paymentNumberService, AuditMetadataFactory auditMetadataFactory, AccountPeriodGuard accountPeriodGuard, PaymentQueryService queryService, FinancePostingService financePostingService, SettlementCurrencyService settlementCurrencyService) { this.paymentMapper = paymentMapper; this.paymentAllocationMapper = paymentAllocationMapper; this.payableMapper = payableMapper; this.paymentNumberService = paymentNumberService; this.auditMetadataFactory = auditMetadataFactory; this.accountPeriodGuard = accountPeriodGuard; this.queryService = queryService; this.financePostingService = financePostingService; this.baseCurrencyService = null; this.settlementCurrencyService = settlementCurrencyService; }

    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) {
        validateCreateRequest(request); accountPeriodGuard.requireOpen(request.paymentDate(), "付款单创建"); AuditMetadata audit = auditMetadataFactory.current(); LocalDateTime now = audit.now(); BigDecimal amount = ScalePrecision.amount(request.amount()); BigDecimal allocatedAmount = allocationTotal(request.allocations()); if (allocatedAmount.compareTo(ZERO_AMOUNT) <= 0) throw new IllegalArgumentException("付款核销金额必须大于0"); if (allocatedAmount.compareTo(amount) > 0) throw new IllegalArgumentException("付款核销金额不能超过付款金额");
        String currencyCode;
        BigDecimal exchangeRate;
        if (settlementCurrencyService != null) {
            SettlementCurrencyService.Resolution resolution = settlementCurrencyService.resolve(request.currencyCode(), request.exchangeRate(), request.paymentDate(), audit);
            currencyCode = resolution.currencyCode();
            exchangeRate = resolution.exchangeRate();
        } else {
            currencyCode = normalizeCurrency(request.currencyCode());
            exchangeRate = normalizeRate(request.exchangeRate());
        }
        PaymentEntity payment = new PaymentEntity(); payment.setCompanyId(audit.companyId()); payment.setAccountBookId(audit.accountBookId()); payment.setPaymentNo(paymentNumberService.nextPaymentNo(request.paymentDate())); payment.setSupplierId(request.supplierId()); payment.setPaymentDate(request.paymentDate()); payment.setAmount(amount); payment.setAllocatedAmount(allocatedAmount); payment.setCurrencyCode(currencyCode); payment.setExchangeRate(exchangeRate); payment.setBaseAmount(amount.multiply(exchangeRate).setScale(6, java.math.RoundingMode.HALF_UP)); payment.setStatus("POSTED"); payment.setDeletedFlag(0); payment.setRemark(request.remark()); setAudit(payment, audit, now); if (paymentMapper.insert(payment) != 1) throw new IllegalStateException("保存付款单失败");
        BigDecimal allocatedBaseTotal = ZERO_AMOUNT;
        BigDecimal reliefTotal = ZERO_AMOUNT;
        for (PaymentAllocationRequest allocation : request.allocations()) {
            AllocationPosting posting = allocatePayable(payment, allocation, audit, now);
            allocatedBaseTotal = allocatedBaseTotal.add(posting.baseAmount());
            reliefTotal = reliefTotal.add(posting.reliefAmount());
        }
        financePostingService.recordPayment(payment, SettlementPostingAmounts.fromAllocations(
                allocatedBaseTotal,
                reliefTotal,
                SettlementPostingAmounts.advanceBaseAmount(amount, allocatedAmount, exchangeRate)
        ).alignCashTo(payment.getBaseAmount()), audit);
        return queryService.detail(payment.getId());
    }
    @Transactional
    public PaymentResponse cancel(Long id, PaymentCancelRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); LocalDateTime now = audit.now(); PaymentEntity payment = queryService.requirePayment(id); if ("CANCELLED".equals(payment.getStatus())) return queryService.toResponse(payment); accountPeriodGuard.requireOpen(payment.getPaymentDate(), "付款单作废"); if (!"POSTED".equals(payment.getStatus())) throw new BusinessConflictException("只有已过账付款单可以作废"); payment.setStatus("CANCELLED"); payment.setCancelReason(request.reason().trim()); payment.setCancelledBy(audit.userId()); payment.setCancelledTime(now); payment.setUpdatedBy(audit.userId()); payment.setUpdatedTime(now); OptimisticLockGuard.requireUpdated(paymentMapper.updateById(payment), "付款单已被其他操作修改，请刷新后重试");
        BigDecimal allocatedBaseTotal = ZERO_AMOUNT;
        BigDecimal reliefTotal = ZERO_AMOUNT;
        for (PaymentAllocationEntity allocation : queryService.allocations(payment)) {
            AllocationPosting posting = revertPayableSettlement(payment, allocation, audit, now);
            allocatedBaseTotal = allocatedBaseTotal.add(posting.baseAmount());
            reliefTotal = reliefTotal.add(posting.reliefAmount());
        }
        financePostingService.recordPaymentCancellation(payment, SettlementPostingAmounts.fromAllocations(
                allocatedBaseTotal,
                reliefTotal,
                SettlementPostingAmounts.advanceBaseAmount(payment.getAmount(), payment.getAllocatedAmount(), CurrencyAmountSupport.rate(payment.getExchangeRate()))
        ).alignCashTo(payment.getBaseAmount()), audit);
        return queryService.detail(id);
    }
    private AllocationPosting allocatePayable(PaymentEntity payment, PaymentAllocationRequest request, AuditMetadata audit, LocalDateTime now) {
        PayableEntity payable = payableMapper.selectById(request.payableId()); if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0 || !Objects.equals(payment.getCompanyId(), payable.getCompanyId()) || !Objects.equals(payment.getAccountBookId(), payable.getAccountBookId())) throw new IllegalArgumentException("应付记录不存在"); if (!"INCREASE".equals(payable.getDirection())) throw new IllegalArgumentException("只能核销增加方向的应付记录"); if (!payment.getSupplierId().equals(payable.getSupplierId())) throw new IllegalArgumentException("付款供应商与应付供应商不一致");
        // 原币核销要求币种一致，否则原币余额与本位币冲减额都失去意义，汇兑损益也无从计算。
        if (!CurrencyAmountSupport.currency(payment.getCurrencyCode()).equals(CurrencyAmountSupport.currency(payable.getCurrencyCode()))) throw new IllegalArgumentException("付款币种与应付币种不一致");
        BigDecimal allocationAmount = ScalePrecision.amount(request.amount()); BigDecimal remaining = remaining(payable.getOriginalAmount(), payable.getSettledAmount()); if (allocationAmount.compareTo(remaining) > 0) throw new IllegalArgumentException("付款核销金额不能超过应付剩余金额");
        // 按结算汇率折算的本位币付款额，与按应付入账汇率折算的本位币冲减额之差，就是本次已实现汇兑损益。
        BigDecimal baseAmount = CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(payment.getExchangeRate()));
        BigDecimal baseSettledAmount = CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(payable.getExchangeRate()));
        PaymentAllocationEntity allocation = new PaymentAllocationEntity(); allocation.setCompanyId(payment.getCompanyId()); allocation.setAccountBookId(payment.getAccountBookId()); allocation.setPaymentId(payment.getId()); allocation.setPayableId(payable.getId()); allocation.setAmount(allocationAmount);
        allocation.setBaseAmount(baseAmount); allocation.setBaseSettledAmount(baseSettledAmount); allocation.setFxGainLossAmount(baseAmount.subtract(baseSettledAmount)); setAudit(allocation, audit, now);
        if (paymentAllocationMapper.insert(allocation) != 1) throw new IllegalStateException("保存付款核销明细失败");
        payable.setSettledAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(payable.getSettledAmount()).add(allocationAmount)));
        payable.setBaseSettledAmount(CurrencyAmountSupport.zero(payable.getBaseSettledAmount()).add(baseSettledAmount));
        payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount())); payable.setUpdatedBy(audit.userId()); payable.setUpdatedTime(now); OptimisticLockGuard.requireUpdated(payableMapper.updateById(payable), "应付记录已被其他操作修改，请刷新后重试");
        return new AllocationPosting(ScalePrecision.amount(baseAmount), ScalePrecision.amount(baseSettledAmount));
    }
    private AllocationPosting revertPayableSettlement(PaymentEntity payment, PaymentAllocationEntity allocation, AuditMetadata audit, LocalDateTime now) {
        PayableEntity payable = payableMapper.selectById(allocation.getPayableId()); if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0 || !Objects.equals(payment.getCompanyId(), payable.getCompanyId()) || !Objects.equals(payment.getAccountBookId(), payable.getAccountBookId())) throw new BusinessConflictException("付款核销的应付记录不存在，不能作废付款单"); BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount()); BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(payable.getSettledAmount())); if (settledAmount.compareTo(allocationAmount) < 0) throw new BusinessConflictException("应付已核销金额小于付款核销金额，不能作废付款单");
        // V165 之前的核销明细没有本位币快照，按单据与子账汇率还原；历史单据汇率恒为 1，冲回口径与改造前一致。
        BigDecimal storedBase = CurrencyAmountSupport.zero(allocation.getBaseAmount());
        BigDecimal storedRelief = CurrencyAmountSupport.zero(allocation.getBaseSettledAmount());
        BigDecimal baseAmount = storedBase.signum() > 0 ? storedBase : CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(payment.getExchangeRate()));
        BigDecimal reliefAmount = storedRelief.signum() > 0 ? storedRelief : CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(payable.getExchangeRate()));
        payable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
        payable.setBaseSettledAmount(subtractToZeroFloor(payable.getBaseSettledAmount(), reliefAmount));
        payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount())); payable.setUpdatedBy(audit.userId()); payable.setUpdatedTime(now); OptimisticLockGuard.requireUpdated(payableMapper.updateById(payable), "应付记录已被其他操作修改，请刷新后重试");
        return new AllocationPosting(ScalePrecision.amount(baseAmount), ScalePrecision.amount(reliefAmount));
    }
    /** 历史行的 base_settled_amount 可能是 V161 的 0 默认值，冲回时不能把子账推成负数。 */
    private BigDecimal subtractToZeroFloor(BigDecimal current, BigDecimal delta) { BigDecimal result = CurrencyAmountSupport.zero(current).subtract(delta); return result.signum() > 0 ? result : ZERO_BASE_AMOUNT; }
    /** 单笔核销的本位币过账口径：按结算汇率折算的金额与按子账入账汇率折算的冲减额。 */
    private record AllocationPosting(BigDecimal baseAmount, BigDecimal reliefAmount) { }
    private void validateCreateRequest(PaymentCreateRequest request) { if (request == null) throw new IllegalArgumentException("付款单请求不能为空"); if (request.allocations() == null || request.allocations().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("付款核销明细不能为空"); }
    private String normalizeCurrency(String value) { return value == null || value.isBlank() ? (baseCurrencyService == null ? "CNY" : baseCurrencyService.current()) : value.trim().toUpperCase(Locale.ROOT); }
    private BigDecimal normalizeRate(BigDecimal value) { if (value == null) return BigDecimal.ONE; if (value.signum() <= 0) throw new IllegalArgumentException("汇率必须大于0"); return value; }
    private BigDecimal allocationTotal(List<PaymentAllocationRequest> allocations) { return ScalePrecision.amount(allocations.stream().map(PaymentAllocationRequest::amount).map(ScalePrecision::zeroDefault).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) { return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))); }
    private String settlementStatus(BigDecimal originalAmount, BigDecimal settledAmount) { BigDecimal settled = ScalePrecision.zeroDefault(settledAmount); if (settled.compareTo(ZERO_AMOUNT) <= 0) return "UNSETTLED"; if (settled.compareTo(ScalePrecision.zeroDefault(originalAmount)) >= 0) return "SETTLED"; return "PARTIALLY_SETTLED"; }
    private void setAudit(PaymentEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); }
    private void setAudit(PaymentAllocationEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); }
}
