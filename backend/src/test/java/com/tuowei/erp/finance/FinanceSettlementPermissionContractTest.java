package com.tuowei.erp.finance;

import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceSettlementPermissionContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ReceiptService receiptService;

    @Test
    @WithErpUser(authorities = "finance:payment:create")
    void paymentCancelRequiresDedicatedCancelPermission() throws Exception {
        mockMvc.perform(post("/api/finance/payments/{id}/cancel", 910001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"录入错误"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithErpUser(authorities = "finance:payment:cancel")
    void paymentCancelDelegatesWhenUserHasCancelPermission() throws Exception {
        when(paymentService.cancel(eq(910001L), any(PaymentCancelRequest.class)))
                .thenReturn(paymentResponse());

        mockMvc.perform(post("/api/finance/payments/{id}/cancel", 910001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"录入错误"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.paymentNo").value("FP-910001"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ArgumentCaptor<PaymentCancelRequest> requestCaptor = ArgumentCaptor.forClass(PaymentCancelRequest.class);
        verify(paymentService).cancel(eq(910001L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reason()).isEqualTo("录入错误");
    }

    @Test
    @WithErpUser(authorities = "finance:receipt:create")
    void receiptCancelRequiresDedicatedCancelPermission() throws Exception {
        mockMvc.perform(post("/api/finance/receipts/{id}/cancel", 920001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"客户回款录入错误"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(receiptService);
    }

    @Test
    @WithErpUser(authorities = "finance:receipt:cancel")
    void receiptCancelDelegatesWhenUserHasCancelPermission() throws Exception {
        when(receiptService.cancel(eq(920001L), any(ReceiptCancelRequest.class)))
                .thenReturn(receiptResponse());

        mockMvc.perform(post("/api/finance/receipts/{id}/cancel", 920001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"客户回款录入错误"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.receiptNo").value("FR-920001"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ArgumentCaptor<ReceiptCancelRequest> requestCaptor = ArgumentCaptor.forClass(ReceiptCancelRequest.class);
        verify(receiptService).cancel(eq(920001L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reason()).isEqualTo("客户回款录入错误");
    }

    private static PaymentResponse paymentResponse() {
        return new PaymentResponse(
                910001L,
                "FP-910001",
                710001L,
                LocalDate.of(2026, 6, 9),
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                "CANCELLED",
                "付款备注",
                "录入错误",
                1001L,
                null,
                List.of()
        );
    }

    private static ReceiptResponse receiptResponse() {
        return new ReceiptResponse(
                920001L,
                "FR-920001",
                720001L,
                LocalDate.of(2026, 6, 9),
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                "CANCELLED",
                "收款备注",
                "客户回款录入错误",
                1001L,
                null,
                List.of()
        );
    }
}
