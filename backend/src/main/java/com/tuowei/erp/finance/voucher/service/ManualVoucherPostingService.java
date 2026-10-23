package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Ledger posting and reversal orchestration for manual vouchers. */
@Service
public class ManualVoucherPostingService {

    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String POSTED_SOURCE_TYPE = "MANUAL";
    private static final String REVERSAL_SOURCE_TYPE = "MANUAL_REVERSAL";

    private final ManualVoucherMapper manualVoucherMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ManualVoucherQueryService queryService;
    private final AttachmentService attachmentService;

    public ManualVoucherPostingService(
            ManualVoucherMapper manualVoucherMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountPeriodGuard accountPeriodGuard,
            AuditMetadataFactory auditMetadataFactory,
            ManualVoucherQueryService queryService,
            AttachmentService attachmentService
    ) {
        this.manualVoucherMapper = manualVoucherMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountPeriodGuard = accountPeriodGuard;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
        this.attachmentService = attachmentService;
    }

    /** Move an approved manual voucher into the shared general-ledger voucher tables. */
    @Transactional
    public void post(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = queryService.requireVoucher(id, audit);
        if (!STATUS_APPROVED.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有审批通过的手工凭证可以过账");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, voucher.getId());
        accountPeriodGuard.requireOpen(voucher.getBizDate(), "手工凭证过账");

        List<ManualVoucherLineEntity> lines = queryService.loadLines(voucher, audit);
        requireBalanced(lines);

        VoucherEntity posted = new VoucherEntity();
        posted.setCompanyId(audit.companyId());
        posted.setAccountBookId(audit.accountBookId());
        posted.setVoucherNo(voucher.getVoucherNo());
        posted.setSourceType(POSTED_SOURCE_TYPE);
        posted.setSourceId(voucher.getId());
        posted.setSourceNo(voucher.getVoucherNo());
        posted.setBizDate(voucher.getBizDate());
        posted.setAmount(voucher.getAmount());
        posted.setStatus(STATUS_POSTED);
        posted.setDeletedFlag(0);
        posted.setRemark(voucher.getRemark());
        posted.setCreatedBy(audit.userId());
        posted.setCreatedTime(now);
        posted.setUpdatedBy(audit.userId());
        posted.setUpdatedTime(now);
        posted.setVersion(0);
        voucherMapper.insert(posted);

        for (ManualVoucherLineEntity line : lines) {
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setCompanyId(audit.companyId());
            entry.setAccountBookId(audit.accountBookId());
            entry.setVoucherId(posted.getId());
            entry.setBizDate(voucher.getBizDate());
            entry.setLineNo(line.getLineNo());
            entry.setSubjectId(line.getSubjectId());
            entry.setSubjectCode(line.getSubjectCode());
            entry.setSubjectName(line.getSubjectName());
            entry.setDebitAmount(ScalePrecision.amount(line.getDebitAmount()));
            entry.setCreditAmount(ScalePrecision.amount(line.getCreditAmount()));
            entry.setSummary(line.getSummary());
            entry.setCreatedBy(audit.userId());
            entry.setCreatedTime(now);
            entry.setUpdatedBy(audit.userId());
            entry.setUpdatedTime(now);
            entry.setVersion(0);
            voucherEntryMapper.insert(entry);
        }

        voucher.setStatus(STATUS_POSTED);
        voucher.setPostedVoucherId(posted.getId());
        voucher.setPostedBy(audit.userId());
        voucher.setPostedTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        manualVoucherMapper.updateById(voucher);
    }

    /** Create an immutable reversal voucher for an already posted manual voucher. */
    @Transactional
    public void cancel(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("作废原因不能为空");
        }
        String cancelReason = reason.trim();
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = queryService.requireVoucher(id, audit);
        if (!STATUS_POSTED.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有已过账的手工凭证可以作废");
        }
        accountPeriodGuard.requireOpen(now.toLocalDate(), "手工凭证作废");

        VoucherEntity originalVoucher = requirePostedVoucher(voucher, audit);
        requireNoReversalVoucher(voucher, audit);
        List<VoucherEntryEntity> originalEntries = loadVoucherEntries(originalVoucher.getId(), audit);
        if (originalEntries == null || originalEntries.isEmpty()) {
            throw new BusinessConflictException("手工凭证原始凭证缺少分录，无法作废");
        }
        requirePostedEntriesBalanced(originalEntries, originalVoucher, voucher);

        VoucherEntity reversalVoucher = insertReversalVoucher(voucher, originalVoucher, cancelReason, audit, now);
        insertReversalEntries(reversalVoucher, originalEntries, audit, now);

        voucher.setStatus(STATUS_CANCELLED);
        voucher.setReversalVoucherId(reversalVoucher.getId());
        voucher.setCancelReason(cancelReason);
        voucher.setCancelledBy(audit.userId());
        voucher.setCancelledTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                manualVoucherMapper.updateById(voucher),
                "手工凭证已被其他操作修改，请刷新后重试"
        );
    }

    private void requireBalanced(List<ManualVoucherLineEntity> lines) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessConflictException("手工凭证至少需要两条分录");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ManualVoucherLineEntity line : lines) {
            totalDebit = totalDebit.add(ScalePrecision.zeroDefault(line.getDebitAmount()));
            totalCredit = totalCredit.add(ScalePrecision.zeroDefault(line.getCreditAmount()));
        }
        if (ScalePrecision.amount(totalDebit).compareTo(ScalePrecision.amount(totalCredit)) != 0) {
            throw new BusinessConflictException("借贷金额不平衡，不能继续");
        }
        if (ScalePrecision.amount(totalDebit).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("凭证金额必须大于零");
        }
    }

    private VoucherEntity requirePostedVoucher(ManualVoucherEntity manualVoucher, AuditMetadata audit) {
        Long postedVoucherId = manualVoucher.getPostedVoucherId();
        if (postedVoucherId == null) {
            throw new BusinessConflictException("手工凭证缺少原始过账凭证，无法作废");
        }
        VoucherEntity posted = voucherMapper.selectById(postedVoucherId);
        if (posted == null
                || !Objects.equals(posted.getCompanyId(), audit.companyId())
                || !Objects.equals(posted.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(posted.getDeletedFlag(), 0)
                || !STATUS_POSTED.equals(posted.getStatus())
                || !POSTED_SOURCE_TYPE.equals(posted.getSourceType())
                || !Objects.equals(posted.getSourceId(), manualVoucher.getId())
                || !Objects.equals(posted.getVoucherNo(), manualVoucher.getVoucherNo())
                || !Objects.equals(posted.getSourceNo(), manualVoucher.getVoucherNo())
                || !sameAmount(posted.getAmount(), manualVoucher.getAmount())) {
            throw new BusinessConflictException("手工凭证原始过账凭证不存在，无法作废");
        }
        return posted;
    }

    private void requireNoReversalVoucher(ManualVoucherEntity manualVoucher, AuditMetadata audit) {
        VoucherEntity reversal = voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntity::getSourceType, REVERSAL_SOURCE_TYPE)
                .eq(VoucherEntity::getSourceId, manualVoucher.getId()));
        if (reversal != null) {
            throw new BusinessConflictException("手工凭证已生成红冲凭证，不能重复作废");
        }
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(left))
                .compareTo(ScalePrecision.amount(ScalePrecision.zeroDefault(right))) == 0;
    }

    private List<VoucherEntryEntity> loadVoucherEntries(Long voucherId, AuditMetadata audit) {
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucherId)
                .orderByAsc(VoucherEntryEntity::getLineNo));
    }

    private void requirePostedEntriesBalanced(
            List<VoucherEntryEntity> entries,
            VoucherEntity originalVoucher,
            ManualVoucherEntity manualVoucher
    ) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (VoucherEntryEntity entry : entries) {
            totalDebit = totalDebit.add(ScalePrecision.zeroDefault(entry.getDebitAmount()));
            totalCredit = totalCredit.add(ScalePrecision.zeroDefault(entry.getCreditAmount()));
        }

        BigDecimal debit = ScalePrecision.amount(totalDebit);
        BigDecimal credit = ScalePrecision.amount(totalCredit);
        if (debit.compareTo(credit) != 0 || debit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("手工凭证原始凭证分录不平衡，无法作废");
        }
        if (debit.compareTo(ScalePrecision.amount(ScalePrecision.zeroDefault(originalVoucher.getAmount()))) != 0
                || debit.compareTo(ScalePrecision.amount(ScalePrecision.zeroDefault(manualVoucher.getAmount()))) != 0) {
            throw new BusinessConflictException("手工凭证原始凭证分录金额不一致，无法作废");
        }
    }

    private VoucherEntity insertReversalVoucher(
            ManualVoucherEntity manualVoucher,
            VoucherEntity originalVoucher,
            String cancelReason,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        VoucherEntity reversal = new VoucherEntity();
        reversal.setCompanyId(audit.companyId());
        reversal.setAccountBookId(audit.accountBookId());
        reversal.setVoucherNo(manualVoucher.getVoucherNo() + "-REV");
        reversal.setSourceType(REVERSAL_SOURCE_TYPE);
        reversal.setSourceId(manualVoucher.getId());
        reversal.setSourceNo(manualVoucher.getVoucherNo());
        reversal.setBizDate(now.toLocalDate());
        reversal.setAmount(originalVoucher.getAmount());
        reversal.setStatus(STATUS_POSTED);
        reversal.setDeletedFlag(0);
        reversal.setRemark("手工凭证红冲: " + originalVoucher.getVoucherNo() + "，原因: " + cancelReason);
        reversal.setCreatedBy(audit.userId());
        reversal.setCreatedTime(now);
        reversal.setUpdatedBy(audit.userId());
        reversal.setUpdatedTime(now);
        reversal.setVersion(0);
        try {
            voucherMapper.insert(reversal);
        } catch (DuplicateKeyException ex) {
            throw new BusinessConflictException("手工凭证已生成红冲凭证，不能重复作废");
        }
        return reversal;
    }

    private void insertReversalEntries(
            VoucherEntity reversalVoucher,
            List<VoucherEntryEntity> originalEntries,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        for (VoucherEntryEntity originalEntry : originalEntries) {
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setCompanyId(audit.companyId());
            entry.setAccountBookId(audit.accountBookId());
            entry.setVoucherId(reversalVoucher.getId());
            entry.setBizDate(reversalVoucher.getBizDate());
            entry.setLineNo(originalEntry.getLineNo());
            entry.setSubjectId(originalEntry.getSubjectId());
            entry.setSubjectCode(originalEntry.getSubjectCode());
            entry.setSubjectName(originalEntry.getSubjectName());
            entry.setDebitAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getCreditAmount())));
            entry.setCreditAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getDebitAmount())));
            entry.setSummary("红冲:" + (originalEntry.getSummary() == null ? "" : originalEntry.getSummary()));
            entry.setCreatedBy(audit.userId());
            entry.setCreatedTime(now);
            entry.setUpdatedBy(audit.userId());
            entry.setUpdatedTime(now);
            entry.setVersion(0);
            voucherEntryMapper.insert(entry);
        }
    }
}
