package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import com.tuowei.erp.finance.voucher.web.VoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.VoucherResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class VoucherQueryService {

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final ExpenseMapper expenseMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public VoucherQueryService(
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            ExpenseMapper expenseMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.expenseMapper = expenseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> list(VoucherPageQuery query) {
        VoucherPageQuery safeQuery = query == null ? new VoucherPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<VoucherEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<VoucherEntity> wrapper = new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(safeQuery.getSourceType())) {
            wrapper.eq(VoucherEntity::getSourceType, safeQuery.getSourceType().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(VoucherEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(VoucherEntity::getBizDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(VoucherEntity::getBizDate, safeQuery.getDateTo());
        }
        wrapper.orderByDesc(VoucherEntity::getBizDate).orderByDesc(VoucherEntity::getId);
        Page<VoucherEntity> result = voucherMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public VoucherResponse detail(Long id) {
        return toResponse(requireVoucher(id));
    }

    @Transactional(readOnly = true)
    public List<VoucherEntryResponse> entries(Long voucherId) {
        VoucherEntity voucher = requireVoucher(voucherId);
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getCompanyId, voucher.getCompanyId())
                        .eq(VoucherEntryEntity::getAccountBookId, voucher.getAccountBookId())
                        .eq(VoucherEntryEntity::getVoucherId, voucher.getId())
                        .orderByAsc(VoucherEntryEntity::getLineNo)
                        .orderByAsc(VoucherEntryEntity::getId))
                .stream()
                .map(this::toEntryResponse)
                .toList();
    }

    private VoucherEntity requireVoucher(Long id) {
        VoucherEntity voucher = voucherMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (voucher == null || voucher.getDeletedFlag() == null || voucher.getDeletedFlag() != 0
                || !Objects.equals(voucher.getCompanyId(), audit.companyId())
                || !Objects.equals(voucher.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("凭证不存在");
        }
        return voucher;
    }

    public VoucherResponse toResponse(VoucherEntity voucher) {
        return new VoucherResponse(
                voucher.getId(),
                voucher.getVoucherNo(),
                voucher.getSourceType(),
                voucher.getSourceId(),
                voucher.getSourceNo(),
                voucher.getBizDate(),
                voucher.getAmount(),
                voucher.getStatus(),
                expenseSource(voucher),
                voucher.getRemark()
        );
    }

    private VoucherResponse.ExpenseSourceSummary expenseSource(VoucherEntity voucher) {
        if (!Objects.equals(voucher.getSourceType(), "EXPENSE") || voucher.getSourceId() == null) {
            return null;
        }
        ExpenseEntity expense = expenseMapper.selectById(voucher.getSourceId());
        if (expense == null
                || expense.getDeletedFlag() == null
                || expense.getDeletedFlag() != 0
                || !Objects.equals(expense.getCompanyId(), voucher.getCompanyId())
                || !Objects.equals(expense.getAccountBookId(), voucher.getAccountBookId())) {
            return null;
        }
        return new VoucherResponse.ExpenseSourceSummary(
                expense.getId(),
                expense.getExpenseNo(),
                expense.getExpenseDate(),
                expense.getStatus(),
                expense.getAmount()
        );
    }

    public VoucherEntryResponse toEntryResponse(VoucherEntryEntity entry) {
        return new VoucherEntryResponse(
                entry.getId(),
                entry.getVoucherId(),
                entry.getBizDate(),
                entry.getLineNo(),
                entry.getSubjectId(),
                entry.getSubjectCode(),
                entry.getSubjectName(),
                entry.getDebitAmount(),
                entry.getCreditAmount(),
                entry.getSummary()
        );
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}
