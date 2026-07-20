package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpeningAccountBalanceImportHandler extends AbstractImportHandler {

    private final AccountSubjectMapper accountSubjectMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;

    public OpeningAccountBalanceImportHandler(
            ImportValidationSupport support,
            AccountSubjectMapper accountSubjectMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper
    ) {
        super(support);
        this.accountSubjectMapper = accountSubjectMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.OPENING_ACCOUNT_BALANCE;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String subjectCode = support.required(raw, "subject_code", errors);
        support.date(raw, "biz_date", errors);
        BigDecimal debitAmount = support.optionalAmount(raw, "debit_amount", BigDecimal.ZERO, errors);
        BigDecimal creditAmount = support.optionalAmount(raw, "credit_amount", BigDecimal.ZERO, errors);
        if (debitAmount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse("debit_amount", "借方金额不能小于0"));
        }
        if (creditAmount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse("credit_amount", "贷方金额不能小于0"));
        }
        if ((debitAmount.compareTo(BigDecimal.ZERO) > 0 && creditAmount.compareTo(BigDecimal.ZERO) > 0)
                || (debitAmount.compareTo(BigDecimal.ZERO) == 0 && creditAmount.compareTo(BigDecimal.ZERO) == 0)) {
            errors.add(new ImportRowErrorResponse("debit_amount", "借方金额和贷方金额必须且只能有一个大于0"));
        }
        AccountSubjectEntity subject = null;
        if (subjectCode != null) {
            subject = accountSubjectMapper.selectOne(new LambdaQueryWrapper<AccountSubjectEntity>()
                    .eq(AccountSubjectEntity::getCompanyId, context.companyId())
                    .eq(AccountSubjectEntity::getAccountBookId, context.accountBookId())
                    .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                    .eq(AccountSubjectEntity::getStatus, "ACTIVE")
                    .eq(AccountSubjectEntity::getDeletedFlag, 0));
            if (subject == null) {
                errors.add(new ImportRowErrorResponse("subject_code", "会计科目不存在或已停用"));
            } else {
                Long childCount = accountSubjectMapper.selectCount(new LambdaQueryWrapper<AccountSubjectEntity>()
                        .eq(AccountSubjectEntity::getCompanyId, context.companyId())
                        .eq(AccountSubjectEntity::getAccountBookId, context.accountBookId())
                        .eq(AccountSubjectEntity::getParentId, subject.getId())
                        .eq(AccountSubjectEntity::getStatus, "ACTIVE")
                        .eq(AccountSubjectEntity::getDeletedFlag, 0));
                if (exists(childCount)) {
                    errors.add(new ImportRowErrorResponse("subject_code", "非叶子科目不能导入期初余额"));
                }
            }
        }
        BigDecimal debitTotal = total(context, "openingDebit").add(debitAmount);
        BigDecimal creditTotal = total(context, "openingCredit").add(creditAmount);
        context.attributes().put("openingDebit", debitTotal);
        context.attributes().put("openingCredit", creditTotal);
        normalized.put("subjectId", subject == null ? null : subject.getId());
        normalized.put("subjectCode", subject == null ? subjectCode : subject.getSubjectCode());
        normalized.put("subjectName", subject == null ? null : subject.getSubjectName());
        normalized.put("bizDate", support.optionalText(raw, "biz_date"));
        normalized.put("debitAmount", debitAmount);
        normalized.put("creditAmount", creditAmount);
        normalized.put("summary", support.optionalText(raw, "summary"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public void afterValidate(List<ImportJobRowEntity> rows) {
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.getValidFlag() == null || row.getValidFlag() != 1)) {
            return;
        }
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            debitTotal = debitTotal.add(decimalValue(normalized, "debitAmount"));
            creditTotal = creditTotal.add(decimalValue(normalized, "creditAmount"));
        }
        if (support.scaleAmount(debitTotal).compareTo(support.scaleAmount(creditTotal)) == 0) {
            return;
        }
        ImportJobRowEntity lastRow = rows.get(rows.size() - 1);
        List<ImportRowErrorResponse> errors = new ArrayList<>(support.errorsFromJson(lastRow.getErrorJson()));
        errors.add(new ImportRowErrorResponse("debit_amount", "期初科目余额借贷合计必须相等"));
        lastRow.setValidFlag(0);
        lastRow.setErrorJson(support.toJson(errors));
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        BatchCommitSession session = beginBatchCommit(job, audit);
        session.inspect(rows);
        session.beforeCommit();
        return session.commit(rows);
    }

    @Override
    public BatchCommitSession beginBatchCommit(ImportJobEntity job, AuditMetadata audit) {
        rejectExistingNormalVouchers(audit);
        return new OpeningAccountBalanceBatchCommitSession(job, audit);
    }

    private final class OpeningAccountBalanceBatchCommitSession implements BatchCommitSession {

        private final ImportJobEntity job;
        private final AuditMetadata audit;
        private BigDecimal debitTotal = BigDecimal.ZERO;
        private BigDecimal creditTotal = BigDecimal.ZERO;
        private LocalDate minBizDate;
        private VoucherEntity voucher;
        private int lineNo = 1;

        private OpeningAccountBalanceBatchCommitSession(ImportJobEntity job, AuditMetadata audit) {
            this.job = job;
            this.audit = audit;
        }

        @Override
        public void inspect(List<ImportJobRowEntity> rows) {
            for (ImportJobRowEntity row : rows) {
                Map<String, Object> normalized = normalized(row);
                debitTotal = debitTotal.add(decimalValue(normalized, "debitAmount"));
                creditTotal = creditTotal.add(decimalValue(normalized, "creditAmount"));
                LocalDate bizDate = dateValue(normalized, "bizDate");
                if (minBizDate == null || bizDate.isBefore(minBizDate)) {
                    minBizDate = bizDate;
                }
            }
        }

        @Override
        public void beforeCommit() {
            debitTotal = support.scaleAmount(debitTotal);
            creditTotal = support.scaleAmount(creditTotal);
            if (debitTotal.compareTo(creditTotal) != 0) {
                throw new IllegalArgumentException("期初科目余额借贷不平衡");
            }
            LocalDateTime now = audit.now();
            String voucherNo = "VO-OPENING-" + job.getId();
            voucher = new VoucherEntity();
            voucher.setCompanyId(audit.companyId());
            voucher.setAccountBookId(audit.accountBookId());
            voucher.setVoucherNo(voucherNo);
            voucher.setSourceType(ImportConstants.OPENING_ACCOUNT_BALANCE);
            voucher.setSourceId(job.getId());
            voucher.setSourceNo(voucherNo);
            voucher.setBizDate(minBizDate);
            voucher.setAmount(debitTotal);
            voucher.setStatus("POSTED");
            voucher.setDeletedFlag(0);
            voucher.setRemark("期初科目余额导入");
            voucher.setCreatedBy(audit.userId());
            voucher.setCreatedTime(now);
            voucher.setUpdatedBy(audit.userId());
            voucher.setUpdatedTime(now);
            voucher.setVersion(0);
            voucherMapper.insert(voucher);
        }

        @Override
        public int commit(List<ImportJobRowEntity> rows) {
            LocalDateTime now = audit.now();
            for (ImportJobRowEntity row : rows) {
                Map<String, Object> normalized = normalized(row);
                VoucherEntryEntity entry = new VoucherEntryEntity();
                entry.setCompanyId(audit.companyId());
                entry.setAccountBookId(audit.accountBookId());
                entry.setVoucherId(voucher.getId());
                entry.setBizDate(dateValue(normalized, "bizDate"));
                entry.setLineNo(lineNo++);
                entry.setSubjectId(longValue(normalized, "subjectId"));
                entry.setSubjectCode(text(normalized, "subjectCode"));
                entry.setSubjectName(text(normalized, "subjectName"));
                entry.setDebitAmount(decimalValue(normalized, "debitAmount"));
                entry.setCreditAmount(decimalValue(normalized, "creditAmount"));
                String summary = text(normalized, "summary");
                entry.setSummary(summary == null ? "期初科目余额" : summary);
                entry.setCreatedBy(audit.userId());
                entry.setCreatedTime(now);
                entry.setUpdatedBy(audit.userId());
                entry.setUpdatedTime(now);
                entry.setVersion(0);
                voucherEntryMapper.insert(entry);
            }
            return rows.size();
        }
    }

    private BigDecimal total(ImportValidationContext context, String key) {
        Object value = context.attributes().get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.ZERO;
    }

    private void rejectExistingNormalVouchers(AuditMetadata audit) {
        Long count = voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntity::getAccountBookId, audit.accountBookId())
                .ne(VoucherEntity::getSourceType, ImportConstants.OPENING_ACCOUNT_BALANCE));
        if (exists(count)) {
            throw new IllegalArgumentException("已有正常凭证数据，不能再导入期初科目余额");
        }
    }
}
