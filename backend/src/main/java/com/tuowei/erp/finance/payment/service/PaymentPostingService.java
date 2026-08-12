package com.tuowei.erp.finance.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Posting and settlement operations for payment management. */
@Service
public class PaymentPostingService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final PayableMapper payableMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final PaymentQueryService paymentQueryService;

    public PaymentPostingService(
            PaymentMapper paymentMapper,
            PaymentAllocationMapper paymentAllocationMapper,
            PayableMapper payableMapper,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            PaymentQueryService paymentQueryService
    ) {
        this.paymentMapper = paymentMapper;
        this.paymentAllocationMapper = paymentAllocationMapper;
        this.payableMapper = payableMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.paymentQueryService = paymentQueryService;
    }

    @Transactional
    public void allocatePayable(
            PaymentEntity payment,
            PaymentAllocationRequest request,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        PayableEntity payable = payableMapper.selectById(request.payableId());
        if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应付记录不存在");
        }
        if (!Objects.equals(payment.getCompanyId(), payable.getCompanyId())
                || !Objects.equals(payment.getAccountBookId(), payable.getAccountBookId())) {
            throw new IllegalArgumentException("应付记录不存在");
        }
        if (!"INCREASE".equals(payable.getDirection())) {
            throw new IllegalArgumentException("只能核销增加方向的应付记录");
        }
        if (!payment.getSupplierId().equals(payable.getSupplierId())) {
            throw new IllegalArgumentException("付款供应商与应付供应商不一致");
        }
        BigDecimal allocationAmount = ScalePrecision.amount(request.amount());
        BigDecimal remaining = remaining(payable.getOriginalAmount(), payable.getSettledAmount());
        if (allocationAmount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("付款核销金额不能超过应付剩余金额");
        }

        PaymentAllocationEntity allocation = new PaymentAllocationEntity();
        allocation.setCompanyId(payment.getCompanyId());
        allocation.setAccountBookId(payment.getAccountBookId());
        allocation.setPaymentId(payment.getId());
        allocation.setPayableId(payable.getId());
        allocation.setAmount(allocationAmount);
        setAudit(allocation, audit, now);
        if (paymentAllocationMapper.insert(allocation) != 1) {
            throw new IllegalStateException("保存付款核销明细失败");
        }

        payable.setSettledAmount(ScalePrecision.amount(
                ScalePrecision.zeroDefault(payable.getSettledAmount()).add(allocationAmount)
        ));
        payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount()));
        payable.setUpdatedBy(audit.userId());
        payable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                payableMapper.updateById(payable),
                "应付记录已被其他操作修改，请刷新后重试"
        );
    }

    @Transactional
    public void revertPayableSettlement(
            PaymentEntity payment,
            PaymentAllocationEntity allocation,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        PayableEntity payable = payableMapper.selectById(allocation.getPayableId());
        if (payable == null || payable.getDeletedFlag() == null || payable.getDeletedFlag() != 0
                || !Objects.equals(payment.getCompanyId(), payable.getCompanyId())
                || !Objects.equals(payment.getAccountBookId(), payable.getAccountBookId())) {
            throw new BusinessConflictException("付款核销的应付记录不存在，不能作废付款单");
        }
        BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount());
        BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(payable.getSettledAmount()));
        if (settledAmount.compareTo(allocationAmount) < 0) {
            throw new BusinessConflictException("应付已核销金额小于付款核销金额，不能作废付款单");
        }
        payable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
        payable.setStatus(settlementStatus(payable.getOriginalAmount(), payable.getSettledAmount()));
        payable.setUpdatedBy(audit.userId());
        payable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                payableMapper.updateById(payable),
                "应付记录已被其他操作修改，请刷新后重试"
        );
    }

    public BigDecimal allocationTotal(List<PaymentAllocationRequest> allocations) {
        return ScalePrecision.amount(allocations.stream()
                .map(PaymentAllocationRequest::amount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))
        );
    }

    private String settlementStatus(BigDecimal originalAmount, BigDecimal settledAmount) {
        BigDecimal settled = ScalePrecision.zeroDefault(settledAmount);
        if (settled.compareTo(ZERO_AMOUNT) <= 0) {
            return "UNSETTLED";
        }
        if (settled.compareTo(ScalePrecision.zeroDefault(originalAmount)) >= 0) {
            return "SETTLED";
        }
        return "PARTIALLY_SETTLED";
    }

    private void setAudit(PaymentAllocationEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
