package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 手工凭证服务：财务人员手工录入的记账凭证全生命周期。
 *
 * 草稿/审批阶段的分录只存在 fin_manual_voucher_line，不进 fin_voucher_entry，因此不影响
 * 总账/月结/对账；过账时才把分录灌入共享的 fin_voucher + fin_voucher_entry，与自动凭证同源。
 *
 * 状态机：DRAFT →submit→ PENDING →approve→ APPROVED →post→ POSTED →cancel→ CANCELLED；
 *          PENDING →reject→ DRAFT。仅 DRAFT 可编辑/删除；过账、作废受期间锁定约束。
 */
@Service
public class ManualVoucherService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String SEQUENCE_BIZ_TYPE = "FIN_MANUAL_VOUCHER";
    private static final String SEQUENCE_BIZ_LABEL = "手工凭证";
    private static final String POSTED_SOURCE_TYPE = "MANUAL";
    private static final String REVERSAL_SOURCE_TYPE = "MANUAL_REVERSAL";

    private final ManualVoucherMapper manualVoucherMapper;
    private final ManualVoucherLineMapper manualVoucherLineMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountSubjectMapper accountSubjectMapper;
    private final AccountPeriodGuard accountPeriodGuard;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final AuditMetadataFactory auditMetadataFactory;

    public ManualVoucherService(
            ManualVoucherMapper manualVoucherMapper,
            ManualVoucherLineMapper manualVoucherLineMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectMapper accountSubjectMapper,
            AccountPeriodGuard accountPeriodGuard,
            SequenceNumberGenerator sequenceNumberGenerator,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.manualVoucherMapper = manualVoucherMapper;
        this.manualVoucherLineMapper = manualVoucherLineMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountSubjectMapper = accountSubjectMapper;
        this.accountPeriodGuard = accountPeriodGuard;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ManualVoucherResponse create(ManualVoucherSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        if (request.bizDate() == null) {
            throw new IllegalArgumentException("凭证日期不能为空");
        }
        List<ManualVoucherLineRequest> lines = validateLines(request.lines());
        BigDecimal amount = totalDebit(lines);

        ManualVoucherEntity voucher = new ManualVoucherEntity();
        voucher.setCompanyId(audit.companyId());
        voucher.setAccountBookId(audit.accountBookId());
        voucher.setVoucherNo(sequenceNumberGenerator.nextNumber(SEQUENCE_BIZ_TYPE, SEQUENCE_BIZ_LABEL, request.bizDate()));
        voucher.setBizDate(request.bizDate());
        voucher.setAmount(amount);
        voucher.setStatus(STATUS_DRAFT);
        voucher.setRemark(request.remark());
        voucher.setDeletedFlag(0);
        voucher.setCreatedBy(audit.userId());
        voucher.setCreatedTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        voucher.setVersion(0);
        manualVoucherMapper.insert(voucher);

        insertLines(voucher, lines, audit, now);
        return toResponse(voucher, audit);
    }

    @Transactional
    public ManualVoucherResponse update(Long id, ManualVoucherSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_DRAFT.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有草稿状态的手工凭证可以编辑");
        }
        if (request.bizDate() == null) {
            throw new IllegalArgumentException("凭证日期不能为空");
        }
        List<ManualVoucherLineRequest> lines = validateLines(request.lines());

        voucher.setBizDate(request.bizDate());
        voucher.setAmount(totalDebit(lines));
        voucher.setRemark(request.remark());
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        manualVoucherMapper.updateById(voucher);

        deleteLines(voucher, audit);
        insertLines(voucher, lines, audit, now);
        return toResponse(voucher, audit);
    }

    @Transactional
    public void submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_DRAFT.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有草稿状态的手工凭证可以提交");
        }
        // 重新校验借贷平衡，防止空草稿或被改脏后提交
        requireBalanced(loadLines(voucher, audit));
        voucher.setStatus(STATUS_PENDING);
        voucher.setSubmittedBy(audit.userId());
        voucher.setSubmittedTime(audit.now());
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(audit.now());
        manualVoucherMapper.updateById(voucher);
    }

    @Transactional
    public void approve(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_PENDING.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有待审批状态的手工凭证可以审批");
        }
        voucher.setStatus(STATUS_APPROVED);
        voucher.setApprovedBy(audit.userId());
        voucher.setApprovedTime(audit.now());
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(audit.now());
        manualVoucherMapper.updateById(voucher);
    }

    @Transactional
    public void reject(Long id, String reason) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_PENDING.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有待审批状态的手工凭证可以驳回");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("驳回原因不能为空");
        }
        voucher.setStatus(STATUS_DRAFT);
        voucher.setRejectReason(reason.trim());
        voucher.setSubmittedBy(null);
        voucher.setSubmittedTime(null);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(audit.now());
        manualVoucherMapper.updateById(voucher);
    }

    /**
     * 过账：把审批通过的手工凭证分录灌入共享 fin_voucher + fin_voucher_entry，进入总账。
     * 受期间锁定约束——凭证日期所属期间必须为 OPEN。
     */
    @Transactional
    public void post(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_APPROVED.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有审批通过的手工凭证可以过账");
        }
        accountPeriodGuard.requireOpen(voucher.getBizDate(), "手工凭证过账");

        List<ManualVoucherLineEntity> lines = loadLines(voucher, audit);
        requireBalancedEntities(lines);

        VoucherEntity posted = new VoucherEntity();
        posted.setCompanyId(audit.companyId());
        posted.setAccountBookId(audit.accountBookId());
        posted.setVoucherNo(voucher.getVoucherNo());
        posted.setSourceType(POSTED_SOURCE_TYPE);
        posted.setSourceId(voucher.getId());
        posted.setSourceNo(voucher.getVoucherNo());
        posted.setBizDate(voucher.getBizDate());
        posted.setAmount(voucher.getAmount());
        posted.setStatus("POSTED");
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

    /**
     * 作废已过账手工凭证：保留原始凭证和分录，另生成已过账红冲凭证使总账抵销。
     * 受期间锁定约束——作废日期所属期间必须为 OPEN。
     */
    @Transactional
    public void cancel(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("作废原因不能为空");
        }
        String cancelReason = reason.trim();
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
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

    @Transactional
    public void delete(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_DRAFT.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有草稿状态的手工凭证可以删除");
        }
        deleteLines(voucher, audit);
        manualVoucherMapper.deleteById(voucher.getId());
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
        wrapper.orderByDesc(ManualVoucherEntity::getBizDate).orderByDesc(ManualVoucherEntity::getId);
        Page<ManualVoucherEntity> result = manualVoucherMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(v -> toResponse(v, audit)).toList()
        );
    }

    @Transactional(readOnly = true)
    public ManualVoucherResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        return toResponse(voucher, audit);
    }

    // ---- 内部辅助 ----

    private List<ManualVoucherLineRequest> validateLines(List<ManualVoucherLineRequest> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("手工凭证至少需要两条分录");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ManualVoucherLineRequest line : lines) {
            if (line.subjectId() == null) {
                throw new IllegalArgumentException("分录会计科目不能为空");
            }
            BigDecimal debit = ScalePrecision.amount(ScalePrecision.zeroDefault(line.debitAmount()));
            BigDecimal credit = ScalePrecision.amount(ScalePrecision.zeroDefault(line.creditAmount()));
            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("分录借贷金额不能为负");
            }
            boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new IllegalArgumentException("每条分录必须且只能填写借方或贷方其一");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("凭证金额必须大于零");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("借贷金额不平衡：借方合计 " + totalDebit + "，贷方合计 " + totalCredit);
        }
        return lines;
    }

    private void requireBalanced(List<ManualVoucherLineEntity> lines) {
        requireBalancedEntities(lines);
    }

    private void requireBalancedEntities(List<ManualVoucherLineEntity> lines) {
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

    private BigDecimal totalDebit(List<ManualVoucherLineRequest> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManualVoucherLineRequest line : lines) {
            total = total.add(ScalePrecision.zeroDefault(line.debitAmount()));
        }
        return ScalePrecision.amount(total);
    }

    private void insertLines(ManualVoucherEntity voucher, List<ManualVoucherLineRequest> lines, AuditMetadata audit, LocalDateTime now) {
        int lineNo = 1;
        for (ManualVoucherLineRequest request : lines) {
            AccountSubjectEntity subject = requireSubject(request.subjectId(), audit);
            ManualVoucherLineEntity line = new ManualVoucherLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setVoucherId(voucher.getId());
            line.setLineNo(lineNo++);
            line.setSubjectId(subject.getId());
            line.setSubjectCode(subject.getSubjectCode());
            line.setSubjectName(subject.getSubjectName());
            line.setDebitAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(request.debitAmount())));
            line.setCreditAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(request.creditAmount())));
            line.setSummary(request.summary());
            line.setDeletedFlag(0);
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            manualVoucherLineMapper.insert(line);
        }
    }

    private void deleteLines(ManualVoucherEntity voucher, AuditMetadata audit) {
        manualVoucherLineMapper.delete(new LambdaQueryWrapper<ManualVoucherLineEntity>()
                .eq(ManualVoucherLineEntity::getCompanyId, audit.companyId())
                .eq(ManualVoucherLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ManualVoucherLineEntity::getVoucherId, voucher.getId()));
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
        reversal.setStatus("POSTED");
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

    private List<ManualVoucherLineEntity> loadLines(ManualVoucherEntity voucher, AuditMetadata audit) {
        return manualVoucherLineMapper.selectList(new LambdaQueryWrapper<ManualVoucherLineEntity>()
                .eq(ManualVoucherLineEntity::getCompanyId, audit.companyId())
                .eq(ManualVoucherLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ManualVoucherLineEntity::getVoucherId, voucher.getId())
                .orderByAsc(ManualVoucherLineEntity::getLineNo));
    }

    private AccountSubjectEntity requireSubject(Long subjectId, AuditMetadata audit) {
        AccountSubjectEntity subject = accountSubjectMapper.selectById(subjectId);
        if (subject == null
                || !Objects.equals(subject.getCompanyId(), audit.companyId())
                || !Objects.equals(subject.getAccountBookId(), audit.accountBookId())
                || subject.getDeletedFlag() == null || subject.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("会计科目不存在");
        }
        if (!"ACTIVE".equals(subject.getStatus())) {
            throw new IllegalArgumentException("会计科目已停用：" + subject.getSubjectCode());
        }
        return subject;
    }

    private ManualVoucherEntity requireVoucher(Long id, AuditMetadata audit) {
        ManualVoucherEntity voucher = manualVoucherMapper.selectById(id);
        if (voucher == null
                || !Objects.equals(voucher.getCompanyId(), audit.companyId())
                || !Objects.equals(voucher.getAccountBookId(), audit.accountBookId())
                || voucher.getDeletedFlag() == null || voucher.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("手工凭证不存在");
        }
        return voucher;
    }

    private ManualVoucherResponse toResponse(ManualVoucherEntity voucher, AuditMetadata audit) {
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
