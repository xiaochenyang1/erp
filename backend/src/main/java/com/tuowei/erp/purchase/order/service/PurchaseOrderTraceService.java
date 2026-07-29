package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderDocumentSummary;
import com.tuowei.erp.purchase.order.web.PurchaseOrderExecutionInfo;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRelatedDocs;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Loads and maps the downstream document chain for a purchase order. */
@Service
public class PurchaseOrderTraceService {

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PayableMapper payableMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final PaymentMapper paymentMapper;
    private final VoucherMapper voucherMapper;
    private final WorkflowService workflowService;

    public PurchaseOrderTraceService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            PayableMapper payableMapper,
            PaymentAllocationMapper paymentAllocationMapper,
            PaymentMapper paymentMapper,
            VoucherMapper voucherMapper,
            WorkflowService workflowService
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.payableMapper = payableMapper;
        this.paymentAllocationMapper = paymentAllocationMapper;
        this.paymentMapper = paymentMapper;
        this.voucherMapper = voucherMapper;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderTraceResponse trace(PurchaseOrderResponse order) {
        Long orderId = order.id();
        List<PurchaseReceiptEntity> receipts = purchaseReceiptMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptEntity>()
                        .eq(PurchaseReceiptEntity::getDeletedFlag, 0)
                        .eq(PurchaseReceiptEntity::getOrderId, orderId)
                        .orderByDesc(PurchaseReceiptEntity::getReceiptDate)
                        .orderByDesc(PurchaseReceiptEntity::getId)
        );
        List<Long> receiptIds = receipts.stream().map(PurchaseReceiptEntity::getId).toList();
        List<PurchaseReturnEntity> returns = receiptIds.isEmpty()
                ? List.of()
                : purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getDeletedFlag, 0)
                .in(PurchaseReturnEntity::getReceiptId, receiptIds)
                .orderByDesc(PurchaseReturnEntity::getReturnDate)
                .orderByDesc(PurchaseReturnEntity::getId));

        List<PayableEntity> payables = loadPayables(receipts, returns);
        List<PaymentEntity> payments = loadPayments(payables);
        List<VoucherEntity> vouchers = loadVouchers(receipts, returns, payments);

        return new PurchaseOrderTraceResponse(
                order,
                workflowService.approvalInfo("PURCHASE_ORDER", orderId),
                executionInfo(order),
                new PurchaseOrderRelatedDocs(
                        receipts.stream().map(this::receiptSummary).toList(),
                        returns.stream().map(this::returnSummary).toList(),
                        payables.stream().map(this::payableSummary).toList(),
                        payments.stream().map(this::paymentSummary).toList(),
                        vouchers.stream().map(this::voucherSummary).toList()
                )
        );
    }

    private PurchaseOrderExecutionInfo executionInfo(PurchaseOrderResponse order) {
        BigDecimal orderedQty = ScalePrecision.zeroDefault(order.totalQuantity());
        BigDecimal receivedQty = order.lines().stream()
                .map(PurchaseOrderLineResponse::receivedQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOrderExecutionInfo(
                ScalePrecision.quantity(orderedQty),
                ScalePrecision.quantity(receivedQty),
                ScalePrecision.quantity(orderedQty.subtract(receivedQty)),
                order.receiptStatus()
        );
    }

    private List<PayableEntity> loadPayables(
            List<PurchaseReceiptEntity> receipts,
            List<PurchaseReturnEntity> returns
    ) {
        List<PayableEntity> result = new ArrayList<>();
        List<Long> receiptIds = receipts.stream().map(PurchaseReceiptEntity::getId).toList();
        if (!receiptIds.isEmpty()) {
            result.addAll(payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getDeletedFlag, 0)
                    .eq(PayableEntity::getSourceType, "PURCHASE_RECEIPT")
                    .in(PayableEntity::getSourceId, receiptIds)));
        }
        List<Long> returnIds = returns.stream().map(PurchaseReturnEntity::getId).toList();
        if (!returnIds.isEmpty()) {
            result.addAll(payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getDeletedFlag, 0)
                    .eq(PayableEntity::getSourceType, "PURCHASE_RETURN")
                    .in(PayableEntity::getSourceId, returnIds)));
        }
        return result;
    }

    private List<PaymentEntity> loadPayments(List<PayableEntity> payables) {
        List<Long> payableIds = payables.stream().map(PayableEntity::getId).toList();
        if (payableIds.isEmpty()) {
            return List.of();
        }
        List<Long> paymentIds = paymentAllocationMapper.selectList(
                        new LambdaQueryWrapper<PaymentAllocationEntity>()
                                .in(PaymentAllocationEntity::getPayableId, payableIds)
                ).stream()
                .map(PaymentAllocationEntity::getPaymentId)
                .distinct()
                .toList();
        if (paymentIds.isEmpty()) {
            return List.of();
        }
        return paymentMapper.selectList(new LambdaQueryWrapper<PaymentEntity>()
                .eq(PaymentEntity::getDeletedFlag, 0)
                .in(PaymentEntity::getId, paymentIds)
                .orderByDesc(PaymentEntity::getPaymentDate)
                .orderByDesc(PaymentEntity::getId));
    }

    private List<VoucherEntity> loadVouchers(
            List<PurchaseReceiptEntity> receipts,
            List<PurchaseReturnEntity> returns,
            List<PaymentEntity> payments
    ) {
        List<VoucherEntity> result = new ArrayList<>();
        addVouchers(result, "PURCHASE_RECEIPT", receipts.stream().map(PurchaseReceiptEntity::getId).toList());
        addVouchers(result, "PURCHASE_RETURN", returns.stream().map(PurchaseReturnEntity::getId).toList());
        addVouchers(result, "PAYMENT", payments.stream().map(PaymentEntity::getId).toList());
        return result;
    }

    private void addVouchers(List<VoucherEntity> result, String sourceType, List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return;
        }
        result.addAll(voucherMapper.selectList(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getDeletedFlag, 0)
                .eq(VoucherEntity::getSourceType, sourceType)
                .in(VoucherEntity::getSourceId, sourceIds)
                .orderByDesc(VoucherEntity::getBizDate)
                .orderByDesc(VoucherEntity::getId)));
    }

    private PurchaseOrderDocumentSummary receiptSummary(PurchaseReceiptEntity receipt) {
        return new PurchaseOrderDocumentSummary(
                receipt.getId(),
                receipt.getReceiptNo(),
                "PURCHASE_RECEIPT",
                receipt.getReceiptDate(),
                receipt.getStatus(),
                documentAmount(receipt.getTotalAmount(), receipt.getTotalTaxAmount())
        );
    }

    private PurchaseOrderDocumentSummary returnSummary(PurchaseReturnEntity purchaseReturn) {
        return new PurchaseOrderDocumentSummary(
                purchaseReturn.getId(),
                purchaseReturn.getReturnNo(),
                "PURCHASE_RETURN",
                purchaseReturn.getReturnDate(),
                purchaseReturn.getStatus(),
                documentAmount(purchaseReturn.getTotalAmount(), purchaseReturn.getTotalTaxAmount())
        );
    }

    private PurchaseOrderDocumentSummary payableSummary(PayableEntity payable) {
        return new PurchaseOrderDocumentSummary(
                payable.getId(),
                payable.getPayableNo(),
                payable.getSourceType(),
                payable.getBizDate(),
                payable.getStatus(),
                payable.getOriginalAmount()
        );
    }

    private PurchaseOrderDocumentSummary paymentSummary(PaymentEntity payment) {
        return new PurchaseOrderDocumentSummary(
                payment.getId(),
                payment.getPaymentNo(),
                "PAYMENT",
                payment.getPaymentDate(),
                payment.getStatus(),
                payment.getAmount()
        );
    }

    private PurchaseOrderDocumentSummary voucherSummary(VoucherEntity voucher) {
        return new PurchaseOrderDocumentSummary(
                voucher.getId(),
                voucher.getVoucherNo(),
                voucher.getSourceType(),
                voucher.getBizDate(),
                voucher.getStatus(),
                voucher.getAmount()
        );
    }

    private BigDecimal documentAmount(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(totalAmount).add(ScalePrecision.zeroDefault(totalTaxAmount))
        );
    }
}
