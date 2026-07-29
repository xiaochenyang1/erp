package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderTraceServiceTest {

    private static final Long ORDER_ID = 6101L;

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;
    @Mock
    private PurchaseReturnMapper purchaseReturnMapper;
    @Mock
    private PayableMapper payableMapper;
    @Mock
    private PaymentAllocationMapper paymentAllocationMapper;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private WorkflowService workflowService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(PurchaseReturnEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(PaymentAllocationEntity.class);
        initTableInfo(PaymentEntity.class);
        initTableInfo(VoucherEntity.class);
    }

    @Test
    void traceBuildsExecutionAndCompleteDownstreamDocumentChain() {
        PurchaseReceiptEntity receipt = receipt();
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        PayableEntity receiptPayable = payable(6301L, "AP-RECEIPT", "PURCHASE_RECEIPT", receipt.getId());
        PayableEntity returnPayable = payable(6302L, "AP-RETURN", "PURCHASE_RETURN", purchaseReturn.getId());
        PaymentAllocationEntity firstAllocation = allocation(6401L, receiptPayable.getId());
        PaymentAllocationEntity duplicatePaymentAllocation = allocation(6401L, returnPayable.getId());
        PaymentEntity payment = payment();
        VoucherEntity receiptVoucher = voucher(6501L, "V-RECEIPT", "PURCHASE_RECEIPT", receipt.getId());
        VoucherEntity returnVoucher = voucher(6502L, "V-RETURN", "PURCHASE_RETURN", purchaseReturn.getId());
        VoucherEntity paymentVoucher = voucher(6503L, "V-PAYMENT", "PAYMENT", payment.getId());
        WorkflowApprovalInfoResponse approvalInfo = approvalInfo();

        when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of(receipt));
        when(purchaseReturnMapper.selectList(any())).thenReturn(List.of(purchaseReturn));
        when(payableMapper.selectList(any()))
                .thenReturn(List.of(receiptPayable), List.of(returnPayable));
        when(paymentAllocationMapper.selectList(any()))
                .thenReturn(List.of(firstAllocation, duplicatePaymentAllocation));
        when(paymentMapper.selectList(any())).thenReturn(List.of(payment));
        when(voucherMapper.selectList(any()))
                .thenReturn(List.of(receiptVoucher), List.of(returnVoucher), List.of(paymentVoucher));
        when(workflowService.approvalInfo("PURCHASE_ORDER", ORDER_ID)).thenReturn(approvalInfo);

        var trace = service().trace(order());

        assertThat(trace.order().id()).isEqualTo(ORDER_ID);
        assertThat(trace.approvalInfo()).isSameAs(approvalInfo);
        assertThat(trace.executionInfo().orderedQty()).isEqualByComparingTo("10.0000");
        assertThat(trace.executionInfo().receivedQty()).isEqualByComparingTo("3.2500");
        assertThat(trace.executionInfo().remainingReceiptQty()).isEqualByComparingTo("6.7500");
        assertThat(trace.executionInfo().receiptStatus()).isEqualTo("PARTIAL_RECEIVED");

        assertThat(trace.relatedDocs().receipts()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(receipt.getId());
            assertThat(summary.documentNo()).isEqualTo("GR-6201");
            assertThat(summary.documentType()).isEqualTo("PURCHASE_RECEIPT");
            assertThat(summary.amount()).isEqualByComparingTo("112.35");
        });
        assertThat(trace.relatedDocs().returns()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(purchaseReturn.getId());
            assertThat(summary.documentType()).isEqualTo("PURCHASE_RETURN");
            assertThat(summary.amount()).isEqualByComparingTo("22.35");
        });
        assertThat(trace.relatedDocs().payables())
                .extracting(summary -> summary.documentNo())
                .containsExactly("AP-RECEIPT", "AP-RETURN");
        assertThat(trace.relatedDocs().payments()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(payment.getId());
            assertThat(summary.documentType()).isEqualTo("PAYMENT");
            assertThat(summary.amount()).isEqualByComparingTo("90.00");
        });
        assertThat(trace.relatedDocs().vouchers())
                .extracting(summary -> summary.documentNo())
                .containsExactly("V-RECEIPT", "V-RETURN", "V-PAYMENT");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptEntity>> receiptQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptMapper).selectList(receiptQuery.capture());
        assertThat(receiptQuery.getValue().getSqlSegment()).contains("deleted_flag", "order_id");
        assertThat(receiptQuery.getValue().getParamNameValuePairs().values()).contains(0, ORDER_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableQueries =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper, times(2)).selectList(payableQueries.capture());
        List<Object> payableQueryParameters = payableQueries.getAllValues().stream()
                .peek(query -> query.getSqlSegment())
                .map(LambdaQueryWrapper::getParamNameValuePairs)
                .map(parameters -> parameters.values())
                .flatMap(Collection::stream)
                .toList();
        assertThat(payableQueryParameters)
                .contains("PURCHASE_RECEIPT", receipt.getId(), "PURCHASE_RETURN", purchaseReturn.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PaymentEntity>> paymentQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paymentMapper).selectList(paymentQuery.capture());
        paymentQuery.getValue().getSqlSegment();
        assertThat(paymentQuery.getValue().getParamNameValuePairs().values())
                .contains(0, payment.getId())
                .filteredOn(payment.getId()::equals)
                .hasSize(1);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<VoucherEntity>> voucherQueries =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherMapper, times(3)).selectList(voucherQueries.capture());
        List<Object> voucherQueryParameters = voucherQueries.getAllValues().stream()
                .peek(query -> query.getSqlSegment())
                .map(LambdaQueryWrapper::getParamNameValuePairs)
                .map(parameters -> parameters.values())
                .flatMap(Collection::stream)
                .toList();
        assertThat(voucherQueryParameters).contains(
                "PURCHASE_RECEIPT", receipt.getId(),
                "PURCHASE_RETURN", purchaseReturn.getId(),
                "PAYMENT", payment.getId()
        );
    }

    @Test
    void traceSkipsAllDownstreamQueriesWhenTheOrderHasNoReceipts() {
        when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of());
        when(workflowService.approvalInfo("PURCHASE_ORDER", ORDER_ID)).thenReturn(approvalInfo());

        var trace = service().trace(order());

        assertThat(trace.relatedDocs().receipts()).isEmpty();
        assertThat(trace.relatedDocs().returns()).isEmpty();
        assertThat(trace.relatedDocs().payables()).isEmpty();
        assertThat(trace.relatedDocs().payments()).isEmpty();
        assertThat(trace.relatedDocs().vouchers()).isEmpty();
        verify(purchaseReturnMapper, never()).selectList(any());
        verifyNoInteractions(payableMapper, paymentAllocationMapper, paymentMapper, voucherMapper);
    }

    private PurchaseOrderTraceService service() {
        return new PurchaseOrderTraceService(
                purchaseReceiptMapper,
                purchaseReturnMapper,
                payableMapper,
                paymentAllocationMapper,
                paymentMapper,
                voucherMapper,
                workflowService
        );
    }

    private PurchaseOrderResponse order() {
        return new PurchaseOrderResponse(
                ORDER_ID,
                "PO-6101",
                6001L,
                "Trace Supplier",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15),
                "APPROVED",
                "APPROVED",
                "PARTIAL_RECEIVED",
                null,
                null,
                null,
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("13"),
                "trace",
                List.of(
                        orderLine(6102L, new BigDecimal("3.25")),
                        orderLine(6103L, null)
                )
        );
    }

    private PurchaseOrderLineResponse orderLine(Long id, BigDecimal receivedQty) {
        return new PurchaseOrderLineResponse(
                id,
                1,
                6002L,
                new BigDecimal("5"),
                new BigDecimal("10"),
                new BigDecimal("13"),
                new BigDecimal("50"),
                new BigDecimal("6.5"),
                receivedQty,
                null,
                null,
                null
        );
    }

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(6201L);
        entity.setOrderId(ORDER_ID);
        entity.setReceiptNo("GR-6201");
        entity.setReceiptDate(LocalDate.of(2026, 7, 5));
        entity.setStatus("POSTED");
        entity.setTotalAmount(new BigDecimal("100.10"));
        entity.setTotalTaxAmount(new BigDecimal("12.25"));
        return entity;
    }

    private PurchaseReturnEntity purchaseReturn() {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(6202L);
        entity.setReceiptId(6201L);
        entity.setReturnNo("PR-6202");
        entity.setReturnDate(LocalDate.of(2026, 7, 8));
        entity.setStatus("POSTED");
        entity.setTotalAmount(new BigDecimal("20.10"));
        entity.setTotalTaxAmount(new BigDecimal("2.25"));
        return entity;
    }

    private PayableEntity payable(Long id, String payableNo, String sourceType, Long sourceId) {
        PayableEntity entity = new PayableEntity();
        entity.setId(id);
        entity.setPayableNo(payableNo);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setBizDate(LocalDate.of(2026, 7, 9));
        entity.setStatus("OPEN");
        entity.setOriginalAmount(new BigDecimal("45.00"));
        return entity;
    }

    private PaymentAllocationEntity allocation(Long paymentId, Long payableId) {
        PaymentAllocationEntity entity = new PaymentAllocationEntity();
        entity.setPaymentId(paymentId);
        entity.setPayableId(payableId);
        return entity;
    }

    private PaymentEntity payment() {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(6401L);
        entity.setPaymentNo("PAY-6401");
        entity.setPaymentDate(LocalDate.of(2026, 7, 10));
        entity.setStatus("CONFIRMED");
        entity.setAmount(new BigDecimal("90.00"));
        return entity;
    }

    private VoucherEntity voucher(Long id, String voucherNo, String sourceType, Long sourceId) {
        VoucherEntity entity = new VoucherEntity();
        entity.setId(id);
        entity.setVoucherNo(voucherNo);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setBizDate(LocalDate.of(2026, 7, 11));
        entity.setStatus("POSTED");
        entity.setAmount(new BigDecimal("45.00"));
        return entity;
    }

    private WorkflowApprovalInfoResponse approvalInfo() {
        return new WorkflowApprovalInfoResponse(6601L, "APPROVED", 6003L, null, null, List.of());
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
