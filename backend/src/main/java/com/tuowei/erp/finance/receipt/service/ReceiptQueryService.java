package com.tuowei.erp.finance.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receipt.mapper.ReceiptAllocationMapper;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptAllocationEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationResponse;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ReceiptQueryService {

    private final ReceiptMapper receiptMapper;
    private final ReceiptAllocationMapper receiptAllocationMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ReceiptQueryService(
            ReceiptMapper receiptMapper,
            ReceiptAllocationMapper receiptAllocationMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.receiptMapper = receiptMapper;
        this.receiptAllocationMapper = receiptAllocationMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public ReceiptResponse detail(Long id) {
        ReceiptEntity receipt = requireReceipt(id);
        return toResponse(receipt, allocationResponses(receipt));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(ReceiptPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReceiptPageQuery safe = query == null ? new ReceiptPageQuery() : query;
        Page<ReceiptEntity> page = new Page<>(normalizePageNo(safe.getPageNo()), normalizePageSize(safe.getPageSize()));
        LambdaQueryWrapper<ReceiptEntity> wrapper = new LambdaQueryWrapper<ReceiptEntity>()
                .eq(ReceiptEntity::getCompanyId, audit.companyId())
                .eq(ReceiptEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceiptEntity::getDeletedFlag, 0);
        if (safe.getCustomerId() != null) wrapper.eq(ReceiptEntity::getCustomerId, safe.getCustomerId());
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(ReceiptEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(ReceiptEntity::getReceiptDate).orderByDesc(ReceiptEntity::getId);
        Page<ReceiptEntity> result = receiptMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(receipt -> toResponse(receipt, allocationResponses(receipt))).toList());
    }

    ReceiptEntity requireReceipt(Long id) {
        ReceiptEntity receipt = receiptMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (receipt == null || receipt.getDeletedFlag() == null || receipt.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), audit.companyId())
                || !Objects.equals(receipt.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("收款单不存在");
        }
        return receipt;
    }

    List<ReceiptAllocationEntity> allocations(ReceiptEntity receipt) {
        return receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
                .eq(ReceiptAllocationEntity::getCompanyId, receipt.getCompanyId())
                .eq(ReceiptAllocationEntity::getAccountBookId, receipt.getAccountBookId())
                .eq(ReceiptAllocationEntity::getReceiptId, receipt.getId())
                .orderByAsc(ReceiptAllocationEntity::getId));
    }

    ReceiptResponse toResponse(ReceiptEntity receipt) {
        return toResponse(receipt, allocationResponses(receipt));
    }

    private List<ReceiptAllocationResponse> allocationResponses(ReceiptEntity receipt) {
        return allocations(receipt).stream()
                .map(entity -> new ReceiptAllocationResponse(entity.getId(), entity.getReceivableId(), entity.getAmount()))
                .toList();
    }

    private ReceiptResponse toResponse(ReceiptEntity receipt, List<ReceiptAllocationResponse> allocations) {
        return new ReceiptResponse(receipt.getId(), receipt.getReceiptNo(), receipt.getCustomerId(), receipt.getReceiptDate(),
                receipt.getAmount(), receipt.getAllocatedAmount(), receipt.getStatus(), receipt.getRemark(),
                receipt.getCancelReason(), receipt.getCancelledBy(), receipt.getCancelledTime(), allocations);
    }

    private long normalizePageNo(Integer value) { return value == null || value < 1 ? 1L : value; }
    private long normalizePageSize(Integer value) { return value == null || value < 1 ? 20L : Math.min(value, 200); }
}
