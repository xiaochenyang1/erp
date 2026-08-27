package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Tenant-scoped idempotent voucher, subject and entry persistence. */
@Service
public class FinanceVoucherPersistenceService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountSubjectMapper accountSubjectMapper;

    public FinanceVoucherPersistenceService(
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectMapper accountSubjectMapper
    ) {
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountSubjectMapper = accountSubjectMapper;
    }

    VoucherEntity insertVoucherIfAbsent(
            String sourceType,
            Long sourceId,
            String sourceNo,
            LocalDate bizDate,
            BigDecimal amount,
            String remark,
            AuditMetadata audit
    ) {
        VoucherEntity existing = voucherMapper.selectOne(sourceWrapper(
                audit,
                sourceType,
                sourceId,
                VoucherEntity::getCompanyId,
                VoucherEntity::getAccountBookId,
                VoucherEntity::getSourceType,
                VoucherEntity::getSourceId
        ));
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = audit.now();
        VoucherEntity entity = new VoucherEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setVoucherNo("VO-" + sourceType + "-" + sourceId);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setSourceNo(sourceNo);
        entity.setBizDate(bizDate);
        entity.setAmount(amount);
        entity.setStatus("POSTED");
        setAudit(entity, remark, audit, now);
        voucherMapper.insert(entity);
        return entity;
    }

    void insertVoucherEntriesIfAbsent(
            VoucherEntity voucher,
            String debitSubjectCode,
            String creditSubjectCode,
            BigDecimal amount,
            String summary,
            AuditMetadata audit
    ) {
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity debitSubject = requireSubjectByCode(debitSubjectCode, audit);
        AccountSubjectEntity creditSubject = requireSubjectByCode(creditSubjectCode, audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, debitSubject, 1, amount, ZERO_AMOUNT, summary, audit, now);
        insertVoucherEntry(voucher, creditSubject, 2, ZERO_AMOUNT, amount, summary, audit, now);
    }

    void insertCostEntriesIfAbsent(
            VoucherEntity voucher,
            String debitSubjectCode,
            String creditSubjectCode,
            BigDecimal amount,
            String summary,
            AuditMetadata audit
    ) {
        BigDecimal scaledAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(amount));
        if (scaledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucher.getId())
                .eq(VoucherEntryEntity::getSubjectCode, debitSubjectCode)
                .eq(VoucherEntryEntity::getDebitAmount, scaledAmount)) > 0
                && voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucher.getId())
                .eq(VoucherEntryEntity::getSubjectCode, creditSubjectCode)
                .eq(VoucherEntryEntity::getCreditAmount, scaledAmount)) > 0) {
            return;
        }
        AccountSubjectEntity debitSubject = requireSubjectByCode(debitSubjectCode, audit);
        AccountSubjectEntity creditSubject = requireSubjectByCode(creditSubjectCode, audit);
        int nextLineNo = nextLineNo(voucher, audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, debitSubject, nextLineNo, scaledAmount, ZERO_AMOUNT, summary, audit, now);
        insertVoucherEntry(voucher, creditSubject, nextLineNo + 1, ZERO_AMOUNT, scaledAmount, summary, audit, now);
    }

    void insertInventoryAdjustmentEntriesIfAbsent(
            VoucherEntity voucher,
            java.util.List<InventoryAdjustmentLineEntity> lines,
            AuditMetadata audit
    ) {
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity inventorySubject = requireSubjectByCode("1001", audit);
        AccountSubjectEntity expenseSubject = requireSubjectByCode("6602", audit);
        LocalDateTime now = audit.now();
        int lineNo = 1;
        for (InventoryAdjustmentLineEntity line : lines) {
            BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(line.getAmount()));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String summary = StringUtils.hasText(line.getReason()) ? line.getReason().trim() : "库存调整凭证";
            if ("IN".equals(line.getDirection())) {
                insertVoucherEntry(voucher, inventorySubject, lineNo++, amount, ZERO_AMOUNT, summary, audit, now);
                insertVoucherEntry(voucher, expenseSubject, lineNo++, ZERO_AMOUNT, amount, summary, audit, now);
            } else if ("OUT".equals(line.getDirection())) {
                insertVoucherEntry(voucher, expenseSubject, lineNo++, amount, ZERO_AMOUNT, summary, audit, now);
                insertVoucherEntry(voucher, inventorySubject, lineNo++, ZERO_AMOUNT, amount, summary, audit, now);
            } else {
                throw new IllegalArgumentException("库存调整方向不正确");
            }
        }
    }

    void insertPurchaseReceiptEntriesIfAbsent(
            VoucherEntity voucher,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            AuditMetadata audit
    ) {
        BigDecimal inventory = ScalePrecision.amount(ScalePrecision.zeroDefault(inventoryAmount));
        BigDecimal tax = ScalePrecision.amount(ScalePrecision.zeroDefault(taxAmount));
        if (tax.compareTo(BigDecimal.ZERO) <= 0) {
            insertVoucherEntriesIfAbsent(voucher, "1001", "2202", inventory, "采购入库凭证", audit);
            return;
        }
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity inventorySubject = requireSubjectByCode("1001", audit);
        AccountSubjectEntity taxSubject = requireSubjectByCode("222101", audit);
        AccountSubjectEntity payableSubject = requireSubjectByCode("2202", audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, inventorySubject, 1, inventory, ZERO_AMOUNT, "采购入库凭证", audit, now);
        insertVoucherEntry(voucher, taxSubject, 2, tax, ZERO_AMOUNT, "采购入库凭证", audit, now);
        insertVoucherEntry(voucher, payableSubject, 3, ZERO_AMOUNT, ScalePrecision.amount(inventory.add(tax)), "采购入库凭证", audit, now);
    }

    void insertPurchaseReturnEntriesIfAbsent(
            VoucherEntity voucher,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            AuditMetadata audit
    ) {
        BigDecimal inventory = ScalePrecision.amount(ScalePrecision.zeroDefault(inventoryAmount));
        BigDecimal tax = ScalePrecision.amount(ScalePrecision.zeroDefault(taxAmount));
        if (tax.compareTo(BigDecimal.ZERO) <= 0) {
            insertVoucherEntriesIfAbsent(voucher, "2202", "1001", inventory, "采购退货凭证", audit);
            return;
        }
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity payableSubject = requireSubjectByCode("2202", audit);
        AccountSubjectEntity inventorySubject = requireSubjectByCode("1001", audit);
        AccountSubjectEntity taxSubject = requireSubjectByCode("222101", audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, payableSubject, 1, ScalePrecision.amount(inventory.add(tax)), ZERO_AMOUNT, "采购退货凭证", audit, now);
        insertVoucherEntry(voucher, inventorySubject, 2, ZERO_AMOUNT, inventory, "采购退货凭证", audit, now);
        insertVoucherEntry(voucher, taxSubject, 3, ZERO_AMOUNT, tax, "采购退货凭证", audit, now);
    }

    boolean hasVoucherEntries(VoucherEntity voucher, AuditMetadata audit) {
        return voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucher.getId())) > 0;
    }

    AccountSubjectEntity requireSubjectByCode(String subjectCode, AuditMetadata audit) {
        AccountSubjectEntity subject = accountSubjectMapper.selectOne(new LambdaQueryWrapper<AccountSubjectEntity>()
                .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                .eq(AccountSubjectEntity::getDeletedFlag, 0));
        if (subject == null) {
            return copyDefaultSubject(subjectCode, audit);
        }
        if (!"ACTIVE".equals(subject.getStatus())) {
            throw new IllegalStateException("会计科目不存在或已停用: " + subjectCode);
        }
        return subject;
    }

    void insertVoucherEntry(
            VoucherEntity voucher,
            AccountSubjectEntity subject,
            int lineNo,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String summary,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setCompanyId(audit.companyId());
        entry.setAccountBookId(audit.accountBookId());
        entry.setVoucherId(voucher.getId());
        entry.setBizDate(voucher.getBizDate());
        entry.setLineNo(lineNo);
        entry.setSubjectId(subject.getId());
        entry.setSubjectCode(subject.getSubjectCode());
        entry.setSubjectName(subject.getSubjectName());
        entry.setDebitAmount(ScalePrecision.amount(debitAmount));
        entry.setCreditAmount(ScalePrecision.amount(creditAmount));
        entry.setSummary(summary);
        entry.setCreatedBy(audit.userId());
        entry.setCreatedTime(now);
        entry.setUpdatedBy(audit.userId());
        entry.setUpdatedTime(now);
        entry.setVersion(0);
        voucherEntryMapper.insert(entry);
    }

    private int nextLineNo(VoucherEntity voucher, AuditMetadata audit) {
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                        .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                        .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                        .eq(VoucherEntryEntity::getVoucherId, voucher.getId())
                        .orderByDesc(VoucherEntryEntity::getLineNo)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(entry -> entry.getLineNo() + 1)
                .orElse(1);
    }

    private AccountSubjectEntity copyDefaultSubject(String subjectCode, AuditMetadata audit) {
        SubjectDefinition definition = defaultSubject(subjectCode);
        if (definition == null) {
            throw new IllegalStateException("会计科目不存在或已停用: " + subjectCode);
        }
        LocalDateTime now = audit.now();
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setSubjectCode(definition.subjectCode());
        entity.setSubjectName(definition.subjectName());
        entity.setParentId(null);
        entity.setSubjectType(definition.subjectType());
        entity.setBalanceDirection(definition.balanceDirection());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("系统自动复制默认会计科目");
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        try {
            accountSubjectMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException ex) {
            AccountSubjectEntity existing = accountSubjectMapper.selectOne(
                    new LambdaQueryWrapper<AccountSubjectEntity>()
                            .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                            .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                            .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                            .eq(AccountSubjectEntity::getStatus, "ACTIVE")
                            .eq(AccountSubjectEntity::getDeletedFlag, 0)
            );
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    private SubjectDefinition defaultSubject(String subjectCode) {
        return switch (subjectCode) {
            case "1001" -> new SubjectDefinition("1001", "库存商品", "ASSET", "DEBIT");
            case "1002" -> new SubjectDefinition("1002", "银行存款", "ASSET", "DEBIT");
            case "1122" -> new SubjectDefinition("1122", "应收账款", "ASSET", "DEBIT");
            case "2202" -> new SubjectDefinition("2202", "应付账款", "LIABILITY", "CREDIT");
            case "222101" -> new SubjectDefinition("222101", "应交税费-进项税额", "LIABILITY", "DEBIT");
            case "5001" -> new SubjectDefinition("5001", "生产成本", "ASSET", "DEBIT");
            case "6001" -> new SubjectDefinition("6001", "主营业务收入", "REVENUE", "CREDIT");
            case "6401" -> new SubjectDefinition("6401", "销售退回", "REVENUE", "DEBIT");
            case "6402" -> new SubjectDefinition("6402", "主营业务成本", "EXPENSE", "DEBIT");
            case "6602" -> new SubjectDefinition("6602", "管理费用", "EXPENSE", "DEBIT");
            default -> null;
        };
    }

    private <T> LambdaQueryWrapper<T> sourceWrapper(
            AuditMetadata audit,
            String sourceType,
            Long sourceId,
            SFunction<T, Long> companyIdColumn,
            SFunction<T, Long> accountBookIdColumn,
            SFunction<T, String> sourceTypeColumn,
            SFunction<T, Long> sourceIdColumn
    ) {
        return new LambdaQueryWrapper<T>()
                .eq(companyIdColumn, audit.companyId())
                .eq(accountBookIdColumn, audit.accountBookId())
                .eq(sourceTypeColumn, sourceType)
                .eq(sourceIdColumn, sourceId);
    }

    private void setAudit(VoucherEntity entity, String remark, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setRemark(remark);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private record SubjectDefinition(String subjectCode, String subjectName, String subjectType, String balanceDirection) {
    }
}
