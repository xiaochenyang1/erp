package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Business-document accounting strategies backed by tenant-scoped persistence. */
@Service
public class FinanceVoucherPostingService {

    private static final String CASH_SUBJECT = "1002";

    private final FinanceVoucherPersistenceService persistenceService;

    @Autowired
    public FinanceVoucherPostingService(FinanceVoucherPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /** Keeps direct construction in existing unit tests compatible. */
    public FinanceVoucherPostingService(
            com.tuowei.erp.finance.voucher.mapper.VoucherMapper voucherMapper,
            com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper voucherEntryMapper,
            com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper accountSubjectMapper
    ) {
        this.persistenceService = new FinanceVoucherPersistenceService(voucherMapper, voucherEntryMapper, accountSubjectMapper);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReceipt(PurchaseReceiptEntity receipt, BigDecimal inventoryAmount, BigDecimal taxAmount, BigDecimal documentAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("PURCHASE_RECEIPT", receipt.getId(), receipt.getReceiptNo(), receipt.getReceiptDate(), documentAmount, "采购入库凭证", audit);
        persistenceService.insertPurchaseReceiptEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReturn(PurchaseReturnEntity purchaseReturn, BigDecimal inventoryAmount, BigDecimal taxAmount, BigDecimal documentAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("PURCHASE_RETURN", purchaseReturn.getId(), purchaseReturn.getReturnNo(), purchaseReturn.getReturnDate(), documentAmount, "采购退货凭证", audit);
        persistenceService.insertPurchaseReturnEntriesIfAbsent(voucher, inventoryAmount, taxAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInventoryAdjustment(InventoryAdjustmentEntity adjustment, List<InventoryAdjustmentLineEntity> lines, AuditMetadata audit) {
        BigDecimal amount = inventoryAdjustmentVoucherAmount(lines);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("INVENTORY_ADJUSTMENT", adjustment.getId(), adjustment.getAdjustmentNo(), adjustment.getAdjustmentDate(), amount, "库存调整凭证", audit);
        persistenceService.insertInventoryAdjustmentEntriesIfAbsent(voucher, lines, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesDelivery(SalesDeliveryEntity delivery, BigDecimal documentAmount, BigDecimal costAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("SALES_DELIVERY", delivery.getId(), delivery.getDeliveryNo(), delivery.getDeliveryDate(), documentAmount, "销售出库凭证", audit);
        persistenceService.insertVoucherEntriesIfAbsent(voucher, "1122", "6001", documentAmount, "销售出库凭证", audit);
        persistenceService.insertCostEntriesIfAbsent(voucher, "6402", "1001", costAmount, "销售出库成本结转", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesReturn(SalesReturnEntity salesReturn, BigDecimal documentAmount, BigDecimal costAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("SALES_RETURN", salesReturn.getId(), salesReturn.getReturnNo(), salesReturn.getReturnDate(), documentAmount, "销售退货凭证", audit);
        persistenceService.insertVoucherEntriesIfAbsent(voucher, "6401", "1122", documentAmount, "销售退货凭证", audit);
        persistenceService.insertCostEntriesIfAbsent(voucher, "1001", "6402", costAmount, "销售退货成本冲回", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordReceipt(ReceiptEntity receipt, BigDecimal settledAmount, BigDecimal advanceAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("RECEIPT", receipt.getId(), receipt.getReceiptNo(), receipt.getReceiptDate(), cashAmount(settledAmount, advanceAmount), "收款单凭证", audit);
        persistenceService.insertCashSettlementEntriesIfAbsent(voucher, CASH_SUBJECT, true, "1122", settledAmount, "2203", advanceAmount, "收款核销应收", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordReceiptCancellation(ReceiptEntity receipt, BigDecimal settledAmount, BigDecimal advanceAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("RECEIPT_REVERSAL", receipt.getId(), receipt.getReceiptNo(), receipt.getReceiptDate(), cashAmount(settledAmount, advanceAmount), "收款单作废冲回凭证", audit);
        persistenceService.insertCashSettlementEntriesIfAbsent(voucher, CASH_SUBJECT, false, "1122", settledAmount, "2203", advanceAmount, "收款作废冲回应收", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPayment(PaymentEntity payment, BigDecimal settledAmount, BigDecimal advanceAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("PAYMENT", payment.getId(), payment.getPaymentNo(), payment.getPaymentDate(), cashAmount(settledAmount, advanceAmount), "付款单凭证", audit);
        persistenceService.insertCashSettlementEntriesIfAbsent(voucher, CASH_SUBJECT, false, "2202", settledAmount, "1123", advanceAmount, "付款核销应付", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPaymentCancellation(PaymentEntity payment, BigDecimal settledAmount, BigDecimal advanceAmount, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent("PAYMENT_REVERSAL", payment.getId(), payment.getPaymentNo(), payment.getPaymentDate(), cashAmount(settledAmount, advanceAmount), "付款单作废冲回凭证", audit);
        persistenceService.insertCashSettlementEntriesIfAbsent(voucher, CASH_SUBJECT, true, "2202", settledAmount, "1123", advanceAmount, "付款作废冲回应付", audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTwoSidedVoucher(String sourceType, Long sourceId, String sourceNo, java.time.LocalDate bizDate, BigDecimal amount, String voucherRemark, String debitSubjectCode, String creditSubjectCode, String summary, AuditMetadata audit) {
        VoucherEntity voucher = persistenceService.insertVoucherIfAbsent(sourceType, sourceId, sourceNo, bizDate, amount, voucherRemark, audit);
        persistenceService.insertVoucherEntriesIfAbsent(voucher, debitSubjectCode, creditSubjectCode, amount, summary, audit);
    }

    private BigDecimal cashAmount(BigDecimal settledAmount, BigDecimal advanceAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(settledAmount).add(ScalePrecision.zeroDefault(advanceAmount)));
    }

    private BigDecimal inventoryAdjustmentVoucherAmount(List<InventoryAdjustmentLineEntity> lines) {
        if (lines == null || lines.isEmpty()) return ScalePrecision.amount(BigDecimal.ZERO);
        return ScalePrecision.amount(lines.stream().map(line -> ScalePrecision.amount(ScalePrecision.zeroDefault(line.getAmount()))).filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
