package com.tuowei.erp.finance.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationResponse;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ReceiptService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final ReceiptMapper receiptMapper;
    private final ReceiptAllocationMapper receiptAllocationMapper;
    private final ReceivableMapper receivableMapper;
    private final ReceiptNumberService receiptNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;

    public ReceiptService(
            ReceiptMapper receiptMapper,
            ReceiptAllocationMapper receiptAllocationMapper,
            ReceivableMapper receivableMapper,
            ReceiptNumberService receiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard
    ) {
        this.receiptMapper = receiptMapper;
        this.receiptAllocationMapper = receiptAllocationMapper;
        this.receivableMapper = receivableMapper;
        this.receiptNumberService = receiptNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
    }

    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest request) {
        validateCreateRequest(request);
        accountPeriodGuard.requireOpen(request.receiptDate(), "收款单创建");
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal allocatedAmount = allocationTotal(request.allocations());
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
            allocateReceivable(receipt, allocation, audit, now);
        }
        return detail(receipt.getId());
    }

    @Transactional(readOnly = true)
    public ReceiptResponse detail(Long id) {
        ReceiptEntity receipt = requireReceipt(id);
        return toResponse(receipt, allocations(receipt));
    }

    @Transactional
    public ReceiptResponse cancel(Long id, ReceiptCancelRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReceiptEntity receipt = requireReceipt(id);
        if ("CANCELLED".equals(receipt.getStatus())) {
            return toResponse(receipt, allocations(receipt));
        }
        accountPeriodGuard.requireOpen(receipt.getReceiptDate(), "收款单作废");
        if (!"POSTED".equals(receipt.getStatus())) {
            throw new BusinessConflictException("只有已过账收款单可以作废");
        }

        receipt.setStatus("CANCELLED");
        receipt.setCancelReason(request.reason().trim());
        receipt.setCancelledBy(audit.userId());
        receipt.setCancelledTime(now);
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receiptMapper.updateById(receipt), "收款单已被其他操作修改，请刷新后重试");

        for (ReceiptAllocationEntity allocation : receiptAllocations(receipt)) {
            revertReceivableSettlement(receipt, allocation, audit, now);
        }
        return detail(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(ReceiptPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReceiptPageQuery safeQuery = query == null ? new ReceiptPageQuery() : query;
        Page<ReceiptEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ReceiptEntity> wrapper = new LambdaQueryWrapper<ReceiptEntity>()
                .eq(ReceiptEntity::getCompanyId, audit.companyId())
                .eq(ReceiptEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceiptEntity::getDeletedFlag, 0);
        if (safeQuery.getCustomerId() != null) {
            wrapper.eq(ReceiptEntity::getCustomerId, safeQuery.getCustomerId());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(ReceiptEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(ReceiptEntity::getReceiptDate).orderByDesc(ReceiptEntity::getId);
        Page<ReceiptEntity> result = receiptMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(entity -> toResponse(entity, allocations(entity))).toList()
        );
    }

    private void allocateReceivable(ReceiptEntity receipt, ReceiptAllocationRequest request, AuditMetadata audit, LocalDateTime now) {
        ReceivableEntity receivable = receivableMapper.selectById(request.receivableId());
        if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应收记录不存在");
        }
        if (!Objects.equals(receipt.getCompanyId(), receivable.getCompanyId())
                || !Objects.equals(receipt.getAccountBookId(), receivable.getAccountBookId())) {
            throw new IllegalArgumentException("应收记录不存在");
        }
        if (!"INCREASE".equals(receivable.getDirection())) {
            throw new IllegalArgumentException("只能核销增加方向的应收记录");
        }
        if (!receipt.getCustomerId().equals(receivable.getCustomerId())) {
            throw new IllegalArgumentException("收款客户与应收客户不一致");
        }
        BigDecimal allocationAmount = ScalePrecision.amount(request.amount());
        BigDecimal remaining = remaining(receivable.getOriginalAmount(), receivable.getSettledAmount());
        if (allocationAmount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("收款核销金额不能超过应收剩余金额");
        }

        ReceiptAllocationEntity allocation = new ReceiptAllocationEntity();
        allocation.setCompanyId(receipt.getCompanyId());
        allocation.setAccountBookId(receipt.getAccountBookId());
        allocation.setReceiptId(receipt.getId());
        allocation.setReceivableId(receivable.getId());
        allocation.setAmount(allocationAmount);
        setAudit(allocation, audit, now);
        if (receiptAllocationMapper.insert(allocation) != 1) {
            throw new IllegalStateException("保存收款核销明细失败");
        }

        receivable.setSettledAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()).add(allocationAmount)));
        receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount()));
        receivable.setUpdatedBy(audit.userId());
        receivable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
    }

    private void revertReceivableSettlement(ReceiptEntity receipt, ReceiptAllocationEntity allocation, AuditMetadata audit, LocalDateTime now) {
        ReceivableEntity receivable = receivableMapper.selectById(allocation.getReceivableId());
        if (receivable == null || receivable.getDeletedFlag() == null || receivable.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), receivable.getCompanyId())
                || !Objects.equals(receipt.getAccountBookId(), receivable.getAccountBookId())) {
            throw new BusinessConflictException("收款核销的应收记录不存在，不能作废收款单");
        }
        BigDecimal allocationAmount = ScalePrecision.amount(allocation.getAmount());
        BigDecimal settledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(receivable.getSettledAmount()));
        if (settledAmount.compareTo(allocationAmount) < 0) {
            throw new BusinessConflictException("应收已核销金额小于收款核销金额，不能作废收款单");
        }
        receivable.setSettledAmount(ScalePrecision.amount(settledAmount.subtract(allocationAmount)));
        receivable.setStatus(settlementStatus(receivable.getOriginalAmount(), receivable.getSettledAmount()));
        receivable.setUpdatedBy(audit.userId());
        receivable.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(receivableMapper.updateById(receivable), "应收记录已被其他操作修改，请刷新后重试");
    }

    private ReceiptEntity requireReceipt(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReceiptEntity receipt = receiptMapper.selectById(id);
        if (receipt == null || receipt.getDeletedFlag() == null || receipt.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), audit.companyId())
                || !Objects.equals(receipt.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("收款单不存在");
        }
        return receipt;
    }

    private List<ReceiptAllocationResponse> allocations(ReceiptEntity receipt) {
        return receiptAllocations(receipt)
                .stream()
                .map(entity -> new ReceiptAllocationResponse(entity.getId(), entity.getReceivableId(), entity.getAmount()))
                .toList();
    }

    private List<ReceiptAllocationEntity> receiptAllocations(ReceiptEntity receipt) {
        return receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
                .eq(ReceiptAllocationEntity::getCompanyId, receipt.getCompanyId())
                .eq(ReceiptAllocationEntity::getAccountBookId, receipt.getAccountBookId())
                .eq(ReceiptAllocationEntity::getReceiptId, receipt.getId())
                .orderByAsc(ReceiptAllocationEntity::getId));
    }

    private BigDecimal allocationTotal(List<ReceiptAllocationRequest> allocations) {
        return ScalePrecision.amount(allocations.stream()
                .map(ReceiptAllocationRequest::amount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
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

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
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

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private ReceiptResponse toResponse(ReceiptEntity receipt, List<ReceiptAllocationResponse> allocations) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getCustomerId(),
                receipt.getReceiptDate(),
                receipt.getAmount(),
                receipt.getAllocatedAmount(),
                receipt.getStatus(),
                receipt.getRemark(),
                receipt.getCancelReason(),
                receipt.getCancelledBy(),
                receipt.getCancelledTime(),
                allocations
        );
    }

    private void setAudit(ReceiptEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void setAudit(ReceiptAllocationEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
