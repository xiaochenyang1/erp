package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** Read-side filtering, tenant isolation, line hydration and response mapping for manual vouchers. */
@Service
public class ManualVoucherQueryService {

    private final ManualVoucherMapper manualVoucherMapper;
    private final ManualVoucherLineMapper manualVoucherLineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ManualVoucherQueryService(
            ManualVoucherMapper manualVoucherMapper,
            ManualVoucherLineMapper manualVoucherLineMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.manualVoucherMapper = manualVoucherMapper;
        this.manualVoucherLineMapper = manualVoucherLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<ManualVoucherResponse> list(ManualVoucherPageQuery query) {
        ManualVoucherPageQuery safeQuery = query == null ? new ManualVoucherPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ManualVoucherEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
        LambdaQueryWrapper<ManualVoucherEntity> wrapper = new LambdaQueryWrapper<ManualVoucherEntity>()
                .eq(ManualVoucherEntity::getCompanyId, audit.companyId())
                .eq(ManualVoucherEntity::getAccountBookId, audit.accountBookId())
                .eq(ManualVoucherEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(safeQuery.getVoucherNo())) {
            wrapper.like(ManualVoucherEntity::getVoucherNo, safeQuery.getVoucherNo().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(ManualVoucherEntity::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(ManualVoucherEntity::getBizDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(ManualVoucherEntity::getBizDate, safeQuery.getDateTo());
        }
        wrapper.orderByDesc(ManualVoucherEntity::getBizDate)
                .orderByDesc(ManualVoucherEntity::getId);
        Page<ManualVoucherEntity> result = manualVoucherMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(voucher -> toResponse(voucher, audit)).toList()
        );
    }

    @Transactional(readOnly = true)
    public ManualVoucherResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        return toResponse(voucher, audit);
    }

    @Transactional(readOnly = true)
    public ManualVoucherEntity requireVoucher(Long id, AuditMetadata audit) {
        ManualVoucherEntity voucher = manualVoucherMapper.selectById(id);
        if (voucher == null
                || !Objects.equals(voucher.getCompanyId(), audit.companyId())
                || !Objects.equals(voucher.getAccountBookId(), audit.accountBookId())
                || voucher.getDeletedFlag() == null || voucher.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("手工凭证不存在");
        }
        return voucher;
    }

    List<ManualVoucherLineEntity> loadLines(ManualVoucherEntity voucher, AuditMetadata audit) {
        return manualVoucherLineMapper.selectList(new LambdaQueryWrapper<ManualVoucherLineEntity>()
                .eq(ManualVoucherLineEntity::getCompanyId, audit.companyId())
                .eq(ManualVoucherLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ManualVoucherLineEntity::getVoucherId, voucher.getId())
                .orderByAsc(ManualVoucherLineEntity::getLineNo));
    }

    public ManualVoucherResponse toResponse(ManualVoucherEntity voucher, AuditMetadata audit) {
        List<ManualVoucherLineResponse> lines = loadLines(voucher, audit).stream()
                .map(line -> new ManualVoucherLineResponse(
                        line.getId(),
                        line.getLineNo(),
                        line.getSubjectId(),
                        line.getSubjectCode(),
                        line.getSubjectName(),
                        line.getDebitAmount(),
                        line.getCreditAmount(),
                        line.getSummary()
                ))
                .toList();
        return new ManualVoucherResponse(
                voucher.getId(),
                voucher.getVoucherNo(),
                voucher.getBizDate(),
                voucher.getAmount(),
                voucher.getStatus(),
                voucher.getRemark(),
                voucher.getPostedVoucherId(),
                voucher.getReversalVoucherId(),
                voucher.getRejectReason(),
                voucher.getCancelReason(),
                voucher.getSubmittedTime(),
                voucher.getApprovedTime(),
                voucher.getPostedTime(),
                voucher.getCancelledTime(),
                voucher.getCreatedTime(),
                lines
        );
    }
}
