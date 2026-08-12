package com.tuowei.erp.finance.receipt.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ReceiptService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final ReceiptMapper receiptMapper;
    private final ReceiptNumberService receiptNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final ReceiptQueryService receiptQueryService;
    private final ReceiptPostingService receiptPostingService;

    public ReceiptService(
            ReceiptMapper receiptMapper,
            ReceiptNumberService receiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            ReceiptQueryService receiptQueryService,
            ReceiptPostingService receiptPostingService
    ) {
        this.receiptMapper = receiptMapper;
        this.receiptNumberService = receiptNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.receiptQueryService = receiptQueryService;
        this.receiptPostingService = receiptPostingService;
    }

    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest request) {
        validateCreateRequest(request);
        accountPeriodGuard.requireOpen(request.receiptDate(), "收款单创建");
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal allocatedAmount = receiptPostingService.allocationTotal(request.allocations());
        if (allocatedAmount.compareTo(ZERO_AMOUNT) <= 0) {
            throw new IllegalArgumentException("收款核销金额必须大于0");
        }
        if (allocatedAmount.compareTo(amount) > 0) {
            throw new IllegalArgumentException("收款核销金额不能超过收款金额");
        }

        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setCompanyId(audit.companyId());
        receipt.setAccountBookId(audit.accountBookId());
        receipt.setReceiptNo(receiptNumberService.nextReceiptNo(request.receiptDate()));
        receipt.setCustomerId(request.customerId());
        receipt.setReceiptDate(request.receiptDate());
        receipt.setAmount(amount);
        receipt.setAllocatedAmount(allocatedAmount);
        receipt.setStatus("POSTED");
        receipt.setDeletedFlag(0);
        receipt.setRemark(request.remark());
        setAudit(receipt, audit, now);
        if (receiptMapper.insert(receipt) != 1) {
            throw new IllegalStateException("保存收款单失败");
        }

        for (ReceiptAllocationRequest allocation : request.allocations()) {
            receiptPostingService.allocateReceivable(receipt, allocation, audit, now);
        }
        return receiptQueryService.detail(receipt.getId());
    }

    @Transactional(readOnly = true)
    public ReceiptResponse detail(Long id) {
        return receiptQueryService.detail(id);
    }

    @Transactional
    public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReceiptEntity receipt = receiptQueryService.requireReceipt(id);
        if ("CANCELLED".equals(receipt.getStatus())) {
            return receiptQueryService.detail(id);
        }
        accountPeriodGuard.requireOpen(receipt.getReceiptDate(), "收款单作废");
        if (!"POSTED".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("只有已过账收款单可以作废");
        }

        receipt.setStatus("CANCELLED");
        receipt.setCancelReason(request.reason().trim());
        receipt.setCancelledBy(audit.userId());
        receipt.setCancelledTime(now);
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receiptMapper.updateById(receipt), "收款单已被其他操作修改，请刷新后重试");

        for (ReceiptAllocationEntity allocation : receiptQueryService.receiptAllocations(receipt)) {
            receiptPostingService.revertReceivableSettlement(receipt, allocation, audit, now);
        }
        return receiptQueryService.detail(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(ReceiptPageQuery query) {
        return receiptQueryService.list(query);
    }

    private void validateCreateRequest(ReceiptCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("收款单请求不能为空");
        }
        if (request.allocations() == null) {
            throw new IllegalArgumentException("收款核销明细不能为空");
        }
        if (request.allocations().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("收款核销明细不能为空");
        }
    }

    private void setAudit(ReceiptEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
