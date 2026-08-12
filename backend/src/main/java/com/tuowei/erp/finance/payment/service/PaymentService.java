package com.tuowei.erp.finance.payment.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PaymentService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final PaymentMapper paymentMapper;
    private final PaymentNumberService paymentNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final PaymentQueryService paymentQueryService;
    private final PaymentPostingService paymentPostingService;

    public PaymentService(
            PaymentMapper paymentMapper,
            PaymentNumberService paymentNumberService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            PaymentQueryService paymentQueryService,
            PaymentPostingService paymentPostingService
    ) {
        this.paymentMapper = paymentMapper;
        this.paymentNumberService = paymentNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.paymentQueryService = paymentQueryService;
        this.paymentPostingService = paymentPostingService;
    }

    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) {
        validateCreateRequest(request);
        accountPeriodGuard.requireOpen(request.paymentDate(), "付款单创建");
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal allocatedAmount = paymentPostingService.allocationTotal(request.allocations());
        if (allocatedAmount.compareTo(ZERO_AMOUNT) <= 0) {
            throw new IllegalArgumentException("付款核销金额必须大于0");
        }
        if (allocatedAmount.compareTo(amount) > 0) {
            throw new IllegalArgumentException("付款核销金额不能超过付款金额");
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setCompanyId(audit.companyId());
        payment.setAccountBookId(audit.accountBookId());
        payment.setPaymentNo(paymentNumberService.nextPaymentNo(request.paymentDate()));
        payment.setSupplierId(request.supplierId());
        payment.setPaymentDate(request.paymentDate());
        payment.setAmount(amount);
        payment.setAllocatedAmount(allocatedAmount);
        payment.setStatus("POSTED");
        payment.setDeletedFlag(0);
        payment.setRemark(request.remark());
        setAudit(payment, audit, now);
        if (paymentMapper.insert(payment) != 1) {
            throw new IllegalStateException("保存付款单失败");
        }

        for (PaymentAllocationRequest allocation : request.allocations()) {
            paymentPostingService.allocatePayable(payment, allocation, audit, now);
        }
        return paymentQueryService.detail(payment.getId());
    }

    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) {
        return paymentQueryService.detail(id);
    }

    @Transactional
    public PaymentResponse cancel(Long id, PaymentCancelRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        PaymentEntity payment = paymentQueryService.requirePayment(id);
        if ("CANCELLED".equals(payment.getStatus())) {
            return paymentQueryService.detail(id);
        }
        accountPeriodGuard.requireOpen(payment.getPaymentDate(), "付款单作废");
        if (!"POSTED".equals(payment.getStatus())) {
            throw new BusinessConflictException("只有已过账付款单可以作废");
        }

        payment.setStatus("CANCELLED");
        payment.setCancelReason(request.reason().trim());
        payment.setCancelledBy(audit.userId());
        payment.setCancelledTime(now);
        payment.setUpdatedBy(audit.userId());
        payment.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(paymentMapper.updateById(payment), "付款单已被其他操作修改，请刷新后重试");

        for (PaymentAllocationEntity allocation : paymentQueryService.paymentAllocations(payment)) {
            paymentPostingService.revertPayableSettlement(payment, allocation, audit, now);
        }
        return paymentQueryService.detail(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentPageQuery query) {
        return paymentQueryService.list(query);
    }

    private void validateCreateRequest(PaymentCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("付款单请求不能为空");
        }
        if (request.allocations() == null) {
            throw new IllegalArgumentException("付款核销明细不能为空");
        }
        if (request.allocations().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("付款核销明细不能为空");
        }
    }

    private void setAudit(PaymentEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
