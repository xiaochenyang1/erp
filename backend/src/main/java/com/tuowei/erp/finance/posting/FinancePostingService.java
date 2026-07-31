package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FinancePostingService {

    private final FinanceSubledgerPostingService subledgerPostingService;
    private final FinanceVoucherPostingService voucherPostingService;

    public FinancePostingService(
            FinanceSubledgerPostingService subledgerPostingService,
            FinanceVoucherPostingService voucherPostingService
    ) {
        this.subledgerPostingService = subledgerPostingService;
        this.voucherPostingService = voucherPostingService;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReceipt(PurchaseReceiptEntity receipt, PurchaseOrderEntity order, AuditMetadata audit) {
        BigDecimal inventoryAmount = inventoryAmount(receipt.getTotalAmount());
        BigDecimal taxAmount = taxAmount(receipt.getTotalTaxAmount());
        BigDecimal amount = documentAmount(receipt.getTotalAmount(), receipt.getTotalTaxAmount());
        subledgerPostingService.recordPayableIfAbsent(
                "PURCHASE_RECEIPT",
                receipt.getId(),
                receipt.getReceiptNo(),
                "INCREASE",
                order.getSupplierId(),
                receipt.getReceiptDate(),
                amount,
                "采购入库形成应付",
                audit
        );
        voucherPostingService.recordPurchaseReceipt(receipt, inventoryAmount, taxAmount, amount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPurchaseReturn(PurchaseReturnEntity purchaseReturn, PurchaseOrderEntity order, AuditMetadata audit) {
        BigDecimal inventoryAmount = inventoryAmount(purchaseReturn.getTotalAmount());
        BigDecimal taxAmount = taxAmount(purchaseReturn.getTotalTaxAmount());
        BigDecimal amount = documentAmount(purchaseReturn.getTotalAmount(), purchaseReturn.getTotalTaxAmount());
        subledgerPostingService.recordPayableIfAbsent(
                "PURCHASE_RETURN",
                purchaseReturn.getId(),
                purchaseReturn.getReturnNo(),
                "DECREASE",
                order.getSupplierId(),
                purchaseReturn.getReturnDate(),
                amount,
                "采购退货冲减应付",
                audit
        );
        voucherPostingService.recordPurchaseReturn(purchaseReturn, inventoryAmount, taxAmount, amount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInventoryAdjustment(
            InventoryAdjustmentEntity adjustment,
            List<InventoryAdjustmentLineEntity> lines,
            AuditMetadata audit
    ) {
        voucherPostingService.recordInventoryAdjustment(adjustment, lines, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesDelivery(
            SalesDeliveryEntity delivery,
            SalesOrderEntity order,
            BigDecimal costAmount,
            AuditMetadata audit
    ) {
        BigDecimal amount = documentAmount(delivery.getTotalAmount(), delivery.getTotalTaxAmount());
        subledgerPostingService.recordReceivableIfAbsent(
                "SALES_DELIVERY",
                delivery.getId(),
                delivery.getDeliveryNo(),
                "INCREASE",
                order.getCustomerId(),
                delivery.getDeliveryDate(),
                amount,
                "销售出库形成应收",
                audit
        );
        voucherPostingService.recordSalesDelivery(delivery, amount, costAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSalesReturn(
            SalesReturnEntity salesReturn,
            SalesOrderEntity order,
            BigDecimal costAmount,
            AuditMetadata audit
    ) {
        BigDecimal amount = documentAmount(salesReturn.getTotalAmount(), salesReturn.getTotalTaxAmount());
        subledgerPostingService.recordReceivableIfAbsent(
                "SALES_RETURN",
                salesReturn.getId(),
                salesReturn.getReturnNo(),
                "DECREASE",
                order.getCustomerId(),
                salesReturn.getReturnDate(),
                amount,
                "销售退货冲减应收",
                audit
        );
        voucherPostingService.recordSalesReturn(salesReturn, amount, costAmount, audit);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProductionIssue(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal issueAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        recordProductionVoucher(
                "PRODUCTION_ISSUE",
                sourceId,
                sourceNo,
                issueAmount,
                bizDate,
                "生产领料凭证",
                "5001",
                "1001",
                "生产领料结转生产成本",
                audit
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProductionCompletion(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal completionAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        recordProductionVoucher(
                "PRODUCTION_COMPLETION",
                sourceId,
                sourceNo,
                completionAmount,
                bizDate,
                "生产完工凭证",
                "1001",
                "5001",
                "生产完工入库结转库存商品",
                audit
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProductionCompletionReversal(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal reversalAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        recordProductionVoucher(
                "PRODUCTION_COMPLETION_REVERSAL",
                sourceId,
                sourceNo,
                reversalAmount,
                bizDate,
                "生产反完工凭证",
                "5001",
                "1001",
                "生产反完工冲回库存商品",
                audit
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordProductionReturn(
            ProductionOrderEntity order,
            Long sourceId,
            String sourceNo,
            BigDecimal returnAmount,
            LocalDate bizDate,
            AuditMetadata audit
    ) {
        recordProductionVoucher(
                "PRODUCTION_RETURN",
                sourceId,
                sourceNo,
                returnAmount,
                bizDate,
                "生产退料凭证",
                "1001",
                "5001",
                "生产退料冲回生产成本",
                audit
        );
    }

    private void recordProductionVoucher(
            String sourceType,
            Long sourceId,
            String sourceNo,
            BigDecimal sourceAmount,
            LocalDate bizDate,
            String voucherRemark,
            String debitSubjectCode,
            String creditSubjectCode,
            String summary,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(sourceAmount));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        voucherPostingService.recordTwoSidedVoucher(
                sourceType,
                sourceId,
                sourceNo,
                bizDate,
                amount,
                voucherRemark,
                debitSubjectCode,
                creditSubjectCode,
                summary,
                audit
        );
    }

    private BigDecimal inventoryAmount(BigDecimal totalAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalAmount));
    }

    private BigDecimal taxAmount(BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalTaxAmount));
    }

    private BigDecimal documentAmount(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(totalAmount).add(ScalePrecision.zeroDefault(totalTaxAmount))
        );
    }
}
