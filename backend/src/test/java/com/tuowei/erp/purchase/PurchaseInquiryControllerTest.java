package com.tuowei.erp.purchase;

import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseInquiryService purchaseInquiryService;

    @MockitoBean
    private SystemLogService systemLogService;

    @Test
    @WithErpUser(authorities = "purchase:inquiry:manage")
    void conversionAlsoRequiresPurchaseOrderCreatePermission() throws Exception {
        mockMvc.perform(post("/api/purchase/inquiries/{id}/convert-to-purchase-order", 5001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(purchaseInquiryService);
    }

    @Test
    @WithErpUser(authorities = "purchase:order:create")
    void conversionAlsoRequiresPurchaseInquiryManagePermission() throws Exception {
        mockMvc.perform(post("/api/purchase/inquiries/{id}/convert-to-purchase-order", 5001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(purchaseInquiryService);
    }

    @Test
    @WithErpUser(authorities = {"purchase:inquiry:manage", "purchase:order:create"})
    void conversionReturnsPurchaseOrderWithStructuredInquiryProvenance() throws Exception {
        when(purchaseInquiryService.convertToPurchaseOrder(5001L)).thenReturn(convertedOrder());

        mockMvc.perform(post("/api/purchase/inquiries/{id}/convert-to-purchase-order", 5001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.orderNo").value("PO202607170001"))
                .andExpect(jsonPath("$.data.sourceInquiryId").value("5001"))
                .andExpect(jsonPath("$.data.sourceInquiryNo").value("RFQ202607170001"))
                .andExpect(jsonPath("$.data.sourceQuoteId").value("7002"))
                .andExpect(jsonPath("$.data.lines[0].sourceInquiryId").value("5001"))
                .andExpect(jsonPath("$.data.lines[0].sourceInquiryLineId").value("6001"));

        verify(purchaseInquiryService).convertToPurchaseOrder(5001L);
    }

    private PurchaseOrderResponse convertedOrder() {
        return new PurchaseOrderResponse(
                8101L,
                "PO202607170001",
                7001L,
                "供应商A",
                LocalDate.of(2026, 7, 17),
                null,
                "DRAFT",
                "NOT_SUBMITTED",
                "NOT_RECEIVED",
                5001L,
                "RFQ202607170001",
                7002L,
                new BigDecimal("10.0000"),
                new BigDecimal("125.00"),
                new BigDecimal("16.25"),
                "来源询价单 RFQ202607170001",
                List.of(new PurchaseOrderLineResponse(
                        8201L,
                        1,
                        9001L,
                        new BigDecimal("10.0000"),
                        new BigDecimal("12.50"),
                        new BigDecimal("13.0000"),
                        new BigDecimal("125.00"),
                        new BigDecimal("16.25"),
                        BigDecimal.ZERO,
                        5001L,
                        6001L,
                        "M8螺栓"
                ))
        );
    }
}
