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
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FinanceVoucherPostingService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final String INVENTORY_SUBJECT_CODE = "1001";
    private static final String PAYABLE_SUBJECT_CODE = "2202";
    private static final String PURCHASE_INPUT_TAX_SUBJECT_CODE = "222101";

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountSubjectMapper accountSubjectMapper;

    public FinanceVoucherPostingService(
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectMapper accountSubjectMapper
    ) {
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountSubjectMapper = accountSubjectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReceipt(
            PurchaseReceiptEntity receipt,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            BigDecimal documentAmount,
            AuditMetadata audit
    ) {
        VoucherEntity voucher = insertVoucherIfAbsent(
                "PURCHASE_RECEIPT",
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getReceiptDate(),
                documentAmount,
                "采购入库凭证",
                audit
        );
        insertPurchaseReceiptEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReturn(
            PurchaseReturnEntity purchaseReturn,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            BigDecimal documentAmount,
            AuditMetadata audit
    ) {
        VoucherEntity voucher = insertVoucherIfAbsent(
                "PURCHASE_RETURN",
                purchaseReturn.getId(),
                purchaseReturn.getReturnNo(),
                purchaseReturn.getReturnDate(),
                documentAmount,
                "采购退货凭证",
                audit
        );
        insertPurchaseReturnEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInventoryAdjustment(
            InventoryAdjustmentEntity adjustment,
            List<InventoryAdjustmentLineEntity> lines,
            AuditMetadata audit
    ) {
        BigDecimal amount = inventoryAdjustmentVoucherAmount(lines);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        VoucherEntity voucher = insertVoucherIfAbsent(
                "INVENTORY_ADJUSTMENT",
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getAdjustmentDate(),
                amount,
                "库存调整凭证",
                audit
        );
        insertInventoryAdjustmentEntriesIfAbsent(voucher, lines, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesDelivery(
            SalesDeliveryEntity delivery,
            BigDecimal documentAmount,
            BigDecimal costAmount,
            AuditMetadata audit
    ) {
        VoucherEntity voucher = insertVoucherIfAbsent(
                "SALES_DELIVERY",
                delivery.getId(),
                delivery.getDeliveryNo(),
                delivery.getDeliveryDate(),
                documentAmount,
                "销售出库凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "1122", "6001", documentAmount, "销售出库凭证", audit);
        insertCostEntriesIfAbsent(voucher, "6402", "1001", costAmount, "销售出库成本结转", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesReturn(
            SalesReturnEntity salesReturn,
            BigDecimal documentAmount,
            BigDecimal costAmount,
            AuditMetadata audit
    ) {
        VoucherEntity voucher = insertVoucherIfAbsent(
                "SALES_RETURN",
                salesReturn.getId(),
                salesReturn.getReturnNo(),
                salesReturn.getReturnDate(),
                documentAmount,
                "销售退货凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "6401", "1122", documentAmount, "销售退货凭证", audit);
        insertCostEntriesIfAbsent(voucher, "1001", "6402", costAmount, "销售退货成本冲回", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTwoSidedVoucher(
            String sourceType,
            Long sourceId,
            String sourceNo,
            LocalDate bizDate,
            BigDecimal amount,
            String voucherRemark,
            String debitSubjectCode,
            String creditSubjectCode,
            String summary,
            AuditMetadata audit
    ) {
        VoucherEntity voucher = insertVoucherIfAbsent(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                voucherRemark,
                audit
        );
        insertVoucherEntriesIfAbsent(
                voucher,
                debitSubjectCode,
                creditSubjectCode,
                amount,
                summary,
                audit
        );
    }

    private VoucherEntity insertVoucherIfAbsent(
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

    private void insertVoucherEntriesIfAbsent(
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

    private void insertInventoryAdjustmentEntriesIfAbsent(
            VoucherEntity voucher,
            List<InventoryAdjustmentLineEntity> lines,
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
            String summary = inventoryAdjustmentSummary(line);
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

    private void insertPurchaseReceiptEntriesIfAbsent(
            VoucherEntity voucher,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            AuditMetadata audit
    ) {
        BigDecimal scaledInventoryAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(inventoryAmount));
        BigDecimal scaledTaxAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(taxAmount));
        if (scaledTaxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            insertVoucherEntriesIfAbsent(voucher, INVENTORY_SUBJECT_CODE, PAYABLE_SUBJECT_CODE, scaledInventoryAmount, "采购入库凭证", audit);
            return;
        }
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity inventorySubject = requireSubjectByCode(INVENTORY_SUBJECT_CODE, audit);
        AccountSubjectEntity taxSubject = requireSubjectByCode(PURCHASE_INPUT_TAX_SUBJECT_CODE, audit);
        AccountSubjectEntity payableSubject = requireSubjectByCode(PAYABLE_SUBJECT_CODE, audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, inventorySubject, 1, scaledInventoryAmount, ZERO_AMOUNT, "采购入库凭证", audit, now);
        insertVoucherEntry(voucher, taxSubject, 2, scaledTaxAmount, ZERO_AMOUNT, "采购入库凭证", audit, now);
        insertVoucherEntry(
                voucher,
                payableSubject,
                3,
                ZERO_AMOUNT,
                ScalePrecision.amount(scaledInventoryAmount.add(scaledTaxAmount)),
                "采购入库凭证",
                audit,
                now
        );
    }

    private void insertPurchaseReturnEntriesIfAbsent(
            VoucherEntity voucher,
            BigDecimal inventoryAmount,
            BigDecimal taxAmount,
            AuditMetadata audit
    ) {
        BigDecimal scaledInventoryAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(inventoryAmount));
        BigDecimal scaledTaxAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(taxAmount));
        if (scaledTaxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            insertVoucherEntriesIfAbsent(voucher, PAYABLE_SUBJECT_CODE, INVENTORY_SUBJECT_CODE, scaledInventoryAmount, "采购退货凭证", audit);
            return;
        }
        if (hasVoucherEntries(voucher, audit)) {
            return;
        }
        AccountSubjectEntity payableSubject = requireSubjectByCode(PAYABLE_SUBJECT_CODE, audit);
        AccountSubjectEntity inventorySubject = requireSubjectByCode(INVENTORY_SUBJECT_CODE, audit);
        AccountSubjectEntity taxSubject = requireSubjectByCode(PURCHASE_INPUT_TAX_SUBJECT_CODE, audit);
        LocalDateTime now = audit.now();
        insertVoucherEntry(voucher, payableSubject, 1,
                ScalePrecision.amount(scaledInventoryAmount.add(scaledTaxAmount)), ZERO_AMOUNT, "采购退货凭证", audit, now);
        insertVoucherEntry(voucher, inventorySubject, 2, ZERO_AMOUNT, scaledInventoryAmount, "采购退货凭证", audit, now);
        insertVoucherEntry(voucher, taxSubject, 3, ZERO_AMOUNT, scaledTaxAmount, "采购退货凭证", audit, now);
    }

    private void insertCostEntriesIfAbsent(
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

    private boolean hasVoucherEntries(VoucherEntity voucher, AuditMetadata audit) {
        return voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucher.getId())) > 0;
    }

    private BigDecimal inventoryAdjustmentVoucherAmount(List<InventoryAdjustmentLineEntity> lines) {
        if (lines == null || lines.isEmpty()) {
            return ZERO_AMOUNT;
        }
        return ScalePrecision.amount(lines.stream()
                .map(line -> ScalePrecision.amount(ScalePrecision.zeroDefault(line.getAmount())))
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String inventoryAdjustmentSummary(InventoryAdjustmentLineEntity line) {
        return StringUtils.hasText(line.getReason()) ? line.getReason().trim() : "库存调整凭证";
    }

    private AccountSubjectEntity requireSubjectByCode(String subjectCode, AuditMetadata audit) {
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
            AccountSubjectEntity existing = accountSubjectMapper.selectOne(new LambdaQueryWrapper<AccountSubjectEntity>()
                    .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                    .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                    .eq(AccountSubjectEntity::getSubjectCode, subjectCode)
                    .eq(AccountSubjectEntity::getStatus, "ACTIVE")
                    .eq(AccountSubjectEntity::getDeletedFlag, 0));
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

    private void insertVoucherEntry(
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

    private record SubjectDefinition(
            String subjectCode,
            String subjectName,
            String subjectType,
            String balanceDirection
    ) {
    }
}
