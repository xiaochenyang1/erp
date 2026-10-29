package com.tuowei.erp.finance.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.web.ManualVoucherLineRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Draft entry and approval commands for manual vouchers. */
@Service
public class ManualVoucherCommandService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";

    private static final String SEQUENCE_BIZ_TYPE = "FIN_MANUAL_VOUCHER";
    private static final String SEQUENCE_BIZ_LABEL = "手工凭证";

    private final ManualVoucherMapper manualVoucherMapper;
    private final ManualVoucherLineMapper manualVoucherLineMapper;
    private final AccountSubjectMapper accountSubjectMapper;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ManualVoucherQueryService manualVoucherQueryService;
    private final AttachmentService attachmentService;

    public ManualVoucherCommandService(
            ManualVoucherMapper manualVoucherMapper,
            ManualVoucherLineMapper manualVoucherLineMapper,
            AccountSubjectMapper accountSubjectMapper,
            SequenceNumberGenerator sequenceNumberGenerator,
            AuditMetadataFactory auditMetadataFactory,
            ManualVoucherQueryService manualVoucherQueryService,
            AttachmentService attachmentService
    ) {
        this.manualVoucherMapper = manualVoucherMapper;
        this.manualVoucherLineMapper = manualVoucherLineMapper;
        this.accountSubjectMapper = accountSubjectMapper;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.manualVoucherQueryService = manualVoucherQueryService;
        this.attachmentService = attachmentService;
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
        return manualVoucherQueryService.toResponse(voucher, audit);
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
        return manualVoucherQueryService.toResponse(voucher, audit);
    }

    @Transactional
    public void submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ManualVoucherEntity voucher = requireVoucher(id, audit);
        if (!STATUS_DRAFT.equals(voucher.getStatus())) {
            throw new BusinessConflictException("只有草稿状态的手工凭证可以提交");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.MANUAL_VOUCHER, voucher.getId());
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

    private void insertLines(
            ManualVoucherEntity voucher,
            List<ManualVoucherLineRequest> lines,
            AuditMetadata audit,
            LocalDateTime now
    ) {
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

    private List<ManualVoucherLineEntity> loadLines(ManualVoucherEntity voucher, AuditMetadata audit) {
        return manualVoucherQueryService.loadLines(voucher, audit);
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
        return manualVoucherQueryService.requireVoucher(id, audit);
    }
}
