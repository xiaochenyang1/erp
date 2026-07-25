package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FinancePostingService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);
    private static final String INVENTORY_SUBJECT_CODE = "1001";
    private static final String PAYABLE_SUBJECT_CODE = "2202";
    private static final String PURCHASE_INPUT_TAX_SUBJECT_CODE = "222101";

    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountSubjectMapper accountSubjectMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;

    public FinancePostingService(
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectMapper accountSubjectMapper,
            CustomerMapper customerMapper,
            SupplierMapper supplierMapper
    ) {
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountSubjectMapper = accountSubjectMapper;
        this.customerMapper = customerMapper;
        this.supplierMapper = supplierMapper;
    }

    @Transactional
    public void recordPurchaseReceipt(PurchaseReceiptEntity receipt, PurchaseOrderEntity order, AuditMetadata audit) {
        String sourceType = "PURCHASE_RECEIPT";
        BigDecimal inventoryAmount = inventoryAmount(receipt.getTotalAmount());
        BigDecimal taxAmount = taxAmount(receipt.getTotalTaxAmount());
        BigDecimal amount = documentAmount(receipt.getTotalAmount(), receipt.getTotalTaxAmount());
        insertPayableIfAbsent(sourceType, receipt.getId(), receipt.getReceiptNo(), "INCREASE",
                order.getSupplierId(), receipt.getReceiptDate(), amount, "采购入库形成应付", audit);
        VoucherEntity voucher = insertVoucherIfAbsent(sourceType, receipt.getId(), receipt.getReceiptNo(), receipt.getReceiptDate(), amount, "采购入库凭证", audit);
        insertPurchaseReceiptEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional
    public void recordPurchaseReturn(PurchaseReturnEntity purchaseReturn, PurchaseOrderEntity order, AuditMetadata audit) {
        String sourceType = "PURCHASE_RETURN";
        BigDecimal inventoryAmount = inventoryAmount(purchaseReturn.getTotalAmount());
        BigDecimal taxAmount = taxAmount(purchaseReturn.getTotalTaxAmount());
        BigDecimal amount = documentAmount(purchaseReturn.getTotalAmount(), purchaseReturn.getTotalTaxAmount());
        insertPayableIfAbsent(sourceType, purchaseReturn.getId(), purchaseReturn.getReturnNo(), "DECREASE",
                order.getSupplierId(), purchaseReturn.getReturnDate(), amount, "采购退货冲减应付", audit);
        VoucherEntity voucher = insertVoucherIfAbsent(sourceType, purchaseReturn.getId(), purchaseReturn.getReturnNo(), purchaseReturn.getReturnDate(), amount, "采购退货凭证", audit);
        insertPurchaseReturnEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional
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

    @Transactional
    public void recordSalesDelivery(SalesDeliveryEntity delivery, SalesOrderEntity order, BigDecimal costAmount, AuditMetadata audit) {
        String sourceType = "SALES_DELIVERY";
        BigDecimal amount = documentAmount(delivery.getTotalAmount(), delivery.getTotalTaxAmount());
        insertReceivableIfAbsent(sourceType, delivery.getId(), delivery.getDeliveryNo(), "INCREASE",
                order.getCustomerId(), delivery.getDeliveryDate(), amount, "销售出库形成应收", audit);
        VoucherEntity voucher = insertVoucherIfAbsent(sourceType, delivery.getId(), delivery.getDeliveryNo(), delivery.getDeliveryDate(), amount, "销售出库凭证", audit);
        insertVoucherEntriesIfAbsent(voucher, "1122", "6001", amount, "销售出库凭证", audit);
        insertCostEntriesIfAbsent(voucher, "6402", "1001", costAmount, "销售出库成本结转", audit);
    }

    @Transactional
    public void recordSalesReturn(SalesReturnEntity salesReturn, SalesOrderEntity order, BigDecimal costAmount, AuditMetadata audit) {
        String sourceType = "SALES_RETURN";
        BigDecimal amount = documentAmount(salesReturn.getTotalAmount(), salesReturn.getTotalTaxAmount());
        insertReceivableIfAbsent(sourceType, salesReturn.getId(), salesReturn.getReturnNo(), "DECREASE",
                order.getCustomerId(), salesReturn.getReturnDate(), amount, "销售退货冲减应收", audit);
        VoucherEntity voucher = insertVoucherIfAbsent(sourceType, salesReturn.getId(), salesReturn.getReturnNo(), salesReturn.getReturnDate(), amount, "销售退货凭证", audit);
        insertVoucherEntriesIfAbsent(voucher, "6401", "1122", amount, "销售退货凭证", audit);
        insertCostEntriesIfAbsent(voucher, "1001", "6402", costAmount, "销售退货成本冲回", audit);
    }

    @Transactional
    public void recordProductionIssue(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal issueAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(issueAmount));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "PRODUCTION_ISSUE";
        VoucherEntity voucher = insertVoucherIfAbsent(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                "生产领料凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "5001", "1001", amount, "生产领料结转生产成本", audit);
    }

    @Transactional
    public void recordProductionCompletion(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal completionAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(completionAmount));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "PRODUCTION_COMPLETION";
        VoucherEntity voucher = insertVoucherIfAbsent(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                "生产完工凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "1001", "5001", amount, "生产完工入库结转库存商品", audit);
    }

    @Transactional
    public void recordProductionCompletionReversal(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal reversalAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(reversalAmount));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "PRODUCTION_COMPLETION_REVERSAL";
        VoucherEntity voucher = insertVoucherIfAbsent(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                "生产反完工凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "5001", "1001", amount, "生产反完工冲回库存商品", audit);
    }

    @Transactional
    public void recordProductionReturn(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal returnAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(returnAmount));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "PRODUCTION_RETURN";
        VoucherEntity voucher = insertVoucherIfAbsent(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                "生产退料凭证",
                audit
        );
        insertVoucherEntriesIfAbsent(voucher, "1001", "5001", amount, "生产退料冲回生产成本", audit);
    }

    private void insertPayableIfAbsent(
            String sourceType,
            Long sourceId,
            String sourceNo,
            String direction,
            Long supplierId,
            LocalDate bizDate,
            BigDecimal amount,
            String remark,
            AuditMetadata audit
    ) {
        if (payableMapper.selectCount(sourceWrapper(
                audit,
                sourceType,
                sourceId,
                PayableEntity::getCompanyId,
                PayableEntity::getAccountBookId,
                PayableEntity::getSourceType,
                PayableEntity::getSourceId
        )) > 0) {
            return;
        }
        LocalDateTime now = audit.now();
        PayableEntity entity = new PayableEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setPayableNo("AP-" + sourceType + "-" + sourceId);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setSourceNo(sourceNo);
        entity.setDirection(direction);
        entity.setSupplierId(supplierId);
        entity.setBizDate(bizDate);
        entity.setDueDate(resolveSupplierDueDate(supplierId, bizDate, audit));
        entity.setOriginalAmount(amount);
        entity.setSettledAmount(ZERO_AMOUNT);
        entity.setStatus("INCREASE".equals(direction) ? "UNSETTLED" : "OFFSET");
        setAudit(entity, remark, audit, now);
        payableMapper.insert(entity);
    }

    private void insertReceivableIfAbsent(
            String sourceType,
            Long sourceId,
            String sourceNo,
            String direction,
            Long customerId,
            LocalDate bizDate,
            BigDecimal amount,
            String remark,
            AuditMetadata audit
    ) {
        if (receivableMapper.selectCount(sourceWrapper(
                audit,
                sourceType,
                sourceId,
                ReceivableEntity::getCompanyId,
                ReceivableEntity::getAccountBookId,
                ReceivableEntity::getSourceType,
                ReceivableEntity::getSourceId
        )) > 0) {
            return;
        }
        LocalDateTime now = audit.now();
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setReceivableNo("AR-" + sourceType + "-" + sourceId);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setSourceNo(sourceNo);
        entity.setDirection(direction);
        entity.setCustomerId(customerId);
        entity.setBizDate(bizDate);
        entity.setDueDate(resolveCustomerDueDate(customerId, bizDate, audit));
        entity.setOriginalAmount(amount);
        entity.setSettledAmount(ZERO_AMOUNT);
        entity.setStatus("INCREASE".equals(direction) ? "UNSETTLED" : "OFFSET");
        setAudit(entity, remark, audit, now);
        receivableMapper.insert(entity);
    }

    private LocalDate resolveCustomerDueDate(Long customerId, LocalDate bizDate, AuditMetadata audit) {
        if (bizDate == null) {
            return null;
        }
        if (customerId == null) {
            return bizDate;
        }
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null
                || !java.util.Objects.equals(customer.getCompanyId(), audit.companyId())
                || !java.util.Objects.equals(customer.getAccountBookId(), audit.accountBookId())) {
            return bizDate;
        }
        return addCreditPeriod(bizDate, customer.getCreditPeriod());
    }

    private LocalDate resolveSupplierDueDate(Long supplierId, LocalDate bizDate, AuditMetadata audit) {
        if (bizDate == null) {
            return null;
        }
        if (supplierId == null) {
            return bizDate;
        }
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null
                || !java.util.Objects.equals(supplier.getCompanyId(), audit.companyId())
                || !java.util.Objects.equals(supplier.getAccountBookId(), audit.accountBookId())) {
            return bizDate;
        }
        return addCreditPeriod(bizDate, supplier.getCreditPeriod());
    }

    private LocalDate addCreditPeriod(LocalDate bizDate, Integer creditPeriod) {
        int days = creditPeriod == null ? 0 : Math.max(creditPeriod, 0);
        return bizDate.plusDays(days);
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

    private BigDecimal inventoryAmount(BigDecimal totalAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalAmount));
    }

    private BigDecimal taxAmount(BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalTaxAmount));
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

    private BigDecimal documentAmount(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalAmount).add(ScalePrecision.zeroDefault(totalTaxAmount)));
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

    private void setAudit(PayableEntity entity, String remark, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setRemark(remark);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void setAudit(ReceivableEntity entity, String remark, AuditMetadata audit, LocalDateTime now) {
        entity.setDeletedFlag(0);
        entity.setRemark(remark);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
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
