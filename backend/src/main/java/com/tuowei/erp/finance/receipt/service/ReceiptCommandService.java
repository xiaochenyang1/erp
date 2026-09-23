package com.tuowei.erp.finance.receipt.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.finance.posting.SettlementPostingAmounts;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.service.ReceiptNumberService;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

@Service
public class ReceiptCommandService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final BigDecimal ZERO_BASE_AMOUNT = CurrencyAmountSupport.base(BigDecimal.ZERO, BigDecimal.ONE);
    private final ReceiptMapper receiptMapper;
    private final ReceiptAllocationMapper receiptAllocationMapper;
    private final ReceivableMapper receivableMapper;
    private final ReceiptNumberService receiptNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final ReceiptQueryService queryService;
    private final FinancePostingService financePostingService;
    private final BaseCurrencyService baseCurrencyService;
    private final SettlementCurrencyService settlementCurrencyService;
    public ReceiptCommandService(ReceiptMapper a, ReceiptAllocationMapper b, ReceivableMapper c, ReceiptNumberService d, AuditMetadataFactory e, AccountPeriodGuard f, ReceiptQueryService g, FinancePostingService h) { this(a,b,c,d,e,f,g,h,null); }

    @Autowired
    public ReceiptCommandService(
            ReceiptMapper receiptMapper,
            ReceiptAllocationMapper receiptAllocationMapper,
            ReceivableMapper receivableMapper,
            ReceiptNumberService receiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            ReceiptQueryService queryService,
            FinancePostingService financePostingService,
            SettlementCurrencyService settlementCurrencyService
    ) {
        this.receiptMapper = receiptMapper;
        this.receiptAllocationMapper = receiptAllocationMapper;
        this.receivableMapper = receivableMapper;
        this.receiptNumberService = receiptNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.queryService = queryService;
        this.financePostingService = financePostingService;
        this.baseCurrencyService = null;
        this.settlementCurrencyService = settlementCurrencyService;
    }

    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest request) {
        validateCreateRequest(request);
        accountPeriodGuard.requireOpen(request.receiptDate(), "收款单创建");
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal allocatedAmount = allocationTotal(request.allocations());
        if (allocatedAmount.compareTo(ZERO_AMOUNT) <= 0) throw new IllegalArgumentException("收款核销金额必须大于0");
        if (allocatedAmount.compareTo(amount) > 0) throw new IllegalArgumentException("收款核销金额不能超过收款金额");
        String currencyCode;
        BigDecimal exchangeRate;
        if (settlementCurrencyService != null) {
            SettlementCurrencyService.Resolution resolution = settlementCurrencyService.resolve(request.currencyCode(), request.exchangeRate(), request.receiptDate(), audit);
            currencyCode = resolution.currencyCode();
            exchangeRate = resolution.exchangeRate();
        } else {
            currencyCode = normalizeCurrency(request.currencyCode());
            exchangeRate = normalizeRate(request.exchangeRate());
        }
        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setCompanyId(audit.companyId()); receipt.setAccountBookId(audit.accountBookId());
        receipt.setReceiptNo(receiptNumberService.nextReceiptNo(request.receiptDate())); receipt.setCustomerId(request.customerId());
        receipt.setReceiptDate(request.receiptDate()); receipt.setAmount(amount); receipt.setAllocatedAmount(allocatedAmount); receipt.setCurrencyCode(currencyCode); receipt.setExchangeRate(exchangeRate); receipt.setBaseAmount(amount.multiply(exchangeRate).setScale(6, java.math.RoundingMode.HALF_UP));
        receipt.setStatus("POSTED"); receipt.setDeletedFlag(0); receipt.setRemark(request.remark()); setAudit(receipt, audit, now);
        if (receiptMapper.insert(receipt) != 1) throw new IllegalStateException("保存收款单失败");
        BigDecimal allocatedBaseTotal = ZERO_AMOUNT;
        BigDecimal reliefTotal = ZERO_AMOUNT;
        for (ReceiptAllocationRequest allocation : request.allocations()) {
            AllocationPosting posting = allocateReceivable(receipt, allocation, audit, now);
            allocatedBaseTotal = allocatedBaseTotal.add(posting.baseAmount());
            reliefTotal = reliefTotal.add(posting.reliefAmount());
        }
        financePostingService.recordReceipt(receipt, SettlementPostingAmounts.fromAllocations(
                allocatedBaseTotal,
                reliefTotal,
                SettlementPostingAmounts.advanceBaseAmount(amount, allocatedAmount, exchangeRate)
        ).alignCashTo(receipt.getBaseAmount()), audit);
        return queryService.detail(receipt.getId());
    }

    @Transactional
    public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReceiptEntity receipt = queryService.requireReceipt(id);
        if ("CANCELLED".equals(receipt.getStatus())) return queryService.toResponse(receipt);
        accountPeriodGuard.requireOpen(receipt.getReceiptDate(), "收款单作废");
        if (!"POSTED".equals(receipt.getStatus())) throw new BusinessConflictException("只有已过账收款单可以作废");
        receipt.setStatus("CANCELLED"); receipt.setCancelReason(request.reason().trim()); receipt.setCancelledBy(audit.userId()); receipt.setCancelledTime(now);
        receipt.setUpdatedBy(audit.userId()); receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receiptMapper.updateById(receipt), "收款单已被其他操作修改，请刷新后重试");
        BigDecimal allocatedBaseTotal = ZERO_AMOUNT;
        BigDecimal reliefTotal = ZERO_AMOUNT;
        for (ReceiptAllocationEntity allocation : queryService.allocations(receipt)) {
            AllocationPosting posting = revertReceivableSettlement(receipt, allocation, audit, now);
            allocatedBaseTotal = allocatedBaseTotal.add(posting.baseAmount());
            reliefTotal = reliefTotal.add(posting.reliefAmount());
        }
        financePostingService.recordReceiptCancellation(receipt, SettlementPostingAmounts.fromAllocations(
                allocatedBaseTotal,
                reliefTotal,
                SettlementPostingAmounts.advanceBaseAmount(receipt.getAmount(), receipt.getAllocatedAmount(), CurrencyAmountSupport.rate(receipt.getExchangeRate()))
        ).alignCashTo(receipt.getBaseAmount()), audit);
        return queryService.detail(id);
    }

    private AllocationPosting allocateReceivable(ReceiptEntity receipt, ReceiptAllocationRequest request, AuditMetadata audit, LocalDateTime now) {
        ReceivableEntity receivable = receivableMapper.selectById(request.receivableId());
        if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), receivable.getCompanyId()) || !Objects.equals(receipt.getAccountBookId(), receivable.getAccountBookId())) throw new IllegalArgumentException("应收记录不存在");
        if (!"INCREASE".equals(receivable.getDirection())) throw new IllegalArgumentException("只能核销增加方向的应收记录");
        if (!receipt.getCustomerId().equals(receivable.getCustomerId())) throw new IllegalArgumentException("收款客户与应收客户不一致");
        // 原币核销要求币种一致，否则原币余额与本位币冲减额都失去意义，汇兑损益也无从计算。
        if (!CurrencyAmountSupport.currency(receipt.getCurrencyCode()).equals(CurrencyAmountSupport.currency(receivable.getCurrencyCode()))) throw new IllegalArgumentException("收款币种与应收币种不一致");
        BigDecimal allocationAmount = ScalePrecision.amount(request.amount());
        BigDecimal remaining = remaining(receivable.getOriginalAmount(), receivable.getSettledAmount());
        if (allocationAmount.compareTo(remaining) > 0) throw new IllegalArgumentException("收款核销金额不能超过应收剩余金额");
        // 按结算汇率折算的本位币收款额，与按应收入账汇率折算的本位币冲减额之差，就是本次已实现汇兑损益。
        BigDecimal baseAmount = CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(receipt.getExchangeRate()));
        BigDecimal baseSettledAmount = CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(receivable.getExchangeRate()));
        ReceiptAllocationEntity allocation = new ReceiptAllocationEntity(); allocation.setCompanyId(receipt.getCompanyId()); allocation.setAccountBookId(receipt.getAccountBookId()); allocation.setReceiptId(receipt.getId()); allocation.setReceivableId(receivable.getId()); allocation.setAmount(allocationAmount);
        allocation.setBaseAmount(baseAmount); allocation.setBaseSettledAmount(baseSettledAmount); allocation.setFxGainLossAmount(baseAmount.subtract(baseSettledAmount)); setAudit(allocation, audit, now);
        if (receiptAllocationMapper.insert(allocation) != 1) throw new IllegalStateException("保存收款核销明细失败");
        receivable.setSettledAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()).add(allocationAmount)));
        receivable.setBaseSettledAmount(CurrencyAmountSupport.zero(receivable.getBaseSettledAmount()).add(baseSettledAmount));
        receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount())); receivable.setUpdatedBy(audit.userId()); receivable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
        return new AllocationPosting(ScalePrecision.amount(baseAmount), ScalePrecision.amount(baseSettledAmount));
    }

    private AllocationPosting revertReceivableSettlement(ReceiptEntity receipt, ReceiptAllocationEntity allocation, AuditMetadata audit, LocalDateTime now) {
        ReceivableEntity receivable = receivableMapper.selectById(allocation.getReceivableId());
        if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0 || !Objects.equals(receipt.getCompanyId(), receivable.getCompanyId()) || !Objects.equals(receipt.getAccountBookId(), receivable.getAccountBookId())) throw new BusinessConflictException("收款核销的应收记录不存在，不能作废收款单");
        BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount()); BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()));
        if (settledAmount.compareTo(allocationAmount) < 0) throw new BusinessConflictException("应收已核销金额小于收款核销金额，不能作废收款单");
        // V165 之前的核销明细没有本位币快照，按单据与子账汇率还原；历史单据汇率恒为 1，冲回口径与改造前一致。
        BigDecimal storedBase = CurrencyAmountSupport.zero(allocation.getBaseAmount());
        BigDecimal storedRelief = CurrencyAmountSupport.zero(allocation.getBaseSettledAmount());
        BigDecimal baseAmount = storedBase.signum() > 0 ? storedBase : CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(receipt.getExchangeRate()));
        BigDecimal reliefAmount = storedRelief.signum() > 0 ? storedRelief : CurrencyAmountSupport.base(allocationAmount, CurrencyAmountSupport.rate(receivable.getExchangeRate()));
        receivable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
        receivable.setBaseSettledAmount(subtractToZeroFloor(receivable.getBaseSettledAmount(), reliefAmount));
        receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount())); receivable.setUpdatedBy(audit.userId()); receivable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
        return new AllocationPosting(ScalePrecision.amount(baseAmount), ScalePrecision.amount(reliefAmount));
    }

    /** 历史行的 base_settled_amount 可能是 V161 的 0 默认值，冲回时不能把子账推成负数。 */
    private BigDecimal subtractToZeroFloor(BigDecimal current, BigDecimal delta) {
        BigDecimal result = CurrencyAmountSupport.zero(current).subtract(delta);
        return result.signum() > 0 ? result : ZERO_BASE_AMOUNT;
    }

    /** 单笔核销的本位币过账口径：按结算汇率折算的金额与按子账入账汇率折算的冲减额。 */
    private record AllocationPosting(BigDecimal baseAmount, BigDecimal reliefAmount) {
    }

    private void validateCreateRequest(ReceiptCreateRequest request) { if (request == null) throw new IllegalArgumentException("收款单请求不能为空"); if (request.allocations() == null || request.allocations().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("收款核销明细不能为空"); }
    private String normalizeCurrency(String value) { return value == null || value.isBlank() ? (baseCurrencyService == null ? "CNY" : baseCurrencyService.current()) : value.trim().toUpperCase(Locale.ROOT); }
    private BigDecimal normalizeRate(BigDecimal value) { if (value == null) return BigDecimal.ONE; if (value.signum() <= 0) throw new IllegalArgumentException("汇率必须大于0"); return value; }
    private BigDecimal allocationTotal(List<ReceiptAllocationRequest> allocations) { return ScalePrecision.amount(allocations.stream().map(ReceiptAllocationRequest::amount).map(ScalePrecision::zeroDefault).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) { return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))); }
    private String settlementStatus(BigDecimal originalAmount, BigDecimal settledAmount) { BigDecimal settled = ScalePrecision.zeroDefault(settledAmount); if (settled.compareTo(ZERO_AMOUNT) <= 0) return "UNSETTLED"; if (settled.compareTo(ScalePrecision.zeroDefault(originalAmount)) >= 0) return "SETTLED"; return "PARTIALLY_SETTLED"; }
    private void setAudit(ReceiptEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); }
    private void setAudit(ReceiptAllocationEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); }
}
