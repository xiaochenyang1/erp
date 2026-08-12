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

/** Read-side query operations for receipt management. */
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
        return toResponse(receipt, allocations(receipt));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(ReceiptPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReceiptPageQuery safeQuery = query == null ? new ReceiptPageQuery() : query;
        Page<ReceiptEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ReceiptEntity> wrapper = buildListQuery(audit, safeQuery);
        Page<ReceiptEntity> result = receiptMapper.selectPage(page, wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(entity, allocations(entity)))
                        .toList()
        );
    }

    public ReceiptEntity requireReceipt(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ReceiptEntity receipt = receiptMapper.selectById(id);
        if (receipt == null || receipt.getDeletedFlag() == null || receipt.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), audit.companyId())
                || !Objects.equals(receipt.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("收款单不存在");
        }
        return receipt;
    }

    public List<ReceiptAllocationResponse> allocations(ReceiptEntity receipt) {
        return receiptAllocations(receipt)
                .stream()
                .map(entity -> new ReceiptAllocationResponse(
                        entity.getId(),
                        entity.getReceivableId(),
                        entity.getAmount()
                ))
                .toList();
    }

    public List<ReceiptAllocationEntity> receiptAllocations(ReceiptEntity receipt) {
        return receiptAllocationMapper.selectList(new LambdaQueryWrapper<ReceiptAllocationEntity>()
                .eq(ReceiptAllocationEntity::getCompanyId, receipt.getCompanyId())
                .eq(ReceiptAllocationEntity::getAccountBookId, receipt.getAccountBookId())
                .eq(ReceiptAllocationEntity::getReceiptId, receipt.getId())
                .orderByAsc(ReceiptAllocationEntity::getId));
    }

    public ReceiptResponse toResponse(ReceiptEntity receipt, List<ReceiptAllocationResponse> allocations) {
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

    private LambdaQueryWrapper<ReceiptEntity> buildListQuery(AuditMetadata audit, ReceiptPageQuery query) {
        LambdaQueryWrapper<ReceiptEntity> wrapper = new LambdaQueryWrapper<ReceiptEntity>()
                .eq(ReceiptEntity::getCompanyId, audit.companyId())
                .eq(ReceiptEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceiptEntity::getDeletedFlag, 0);

        if (query.getCustomerId() != null) {
            wrapper.eq(ReceiptEntity::getCustomerId, query.getCustomerId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ReceiptEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }

        return wrapper.orderByDesc(ReceiptEntity::getReceiptDate).orderByDesc(ReceiptEntity::getId);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
