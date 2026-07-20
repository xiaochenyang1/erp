package com.tuowei.erp.purchase;

import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseReceiptReturnExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseReceiptService purchaseReceiptService;

    @MockitoBean
    private PurchaseReturnService purchaseReturnService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void receiptExportRequiresReceiptViewPermission() throws Exception {
        mockMvc.perform(get("/api/purchase/receipts/export")
                        .param("keyword", "GR-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(purchaseReceiptService);
    }

    @Test
    @WithErpUser(authorities = "purchase:receipt:view")
    void receiptExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("receiptNo,totalAmount\r\nGR-001,120.00\r\n");
        when(purchaseReceiptService.exportReceipts(any(PurchaseReceiptPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/purchase/receipts/export")
                        .param("keyword", "GR-001")
                        .param("orderId", "6001")
                        .param("warehouseId", "3001")
                        .param("status", "POSTED")
                        .param("receiptDateFrom", "2026-06-01")
                        .param("receiptDateTo", "2026-06-30"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("receiptNo,totalAmount\r\nGR-001,120.00\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "purchase-receipts.csv");

        ArgumentCaptor<PurchaseReceiptPageQuery> captor = ArgumentCaptor.forClass(PurchaseReceiptPageQuery.class);
        verify(purchaseReceiptService).exportReceipts(captor.capture());
        PurchaseReceiptPageQuery query = captor.getValue();
        assertThat(query.getKeyword()).isEqualTo("GR-001");
        assertThat(query.getOrderId()).isEqualTo(6001L);
        assertThat(query.getWarehouseId()).isEqualTo(3001L);
        assertThat(query.getStatus()).isEqualTo("POSTED");
        assertThat(query.getReceiptDateFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(query.getReceiptDateTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @WithErpUser(authorities = "purchase:return:view")
    void returnExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("returnNo,totalAmount\r\nPR-001,20.00\r\n");
        when(purchaseReturnService.exportReturns(any(PurchaseReturnPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/purchase/returns/export")
                        .param("keyword", "PR-001")
                        .param("receiptId", "7001")
                        .param("warehouseId", "3001")
                        .param("status", "POSTED")
                        .param("returnDateFrom", "2026-06-01")
                        .param("returnDateTo", "2026-06-30"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("returnNo,totalAmount\r\nPR-001,20.00\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "purchase-returns.csv");

        ArgumentCaptor<PurchaseReturnPageQuery> captor = ArgumentCaptor.forClass(PurchaseReturnPageQuery.class);
        verify(purchaseReturnService).exportReturns(captor.capture());
        PurchaseReturnPageQuery query = captor.getValue();
        assertThat(query.getKeyword()).isEqualTo("PR-001");
        assertThat(query.getReceiptId()).isEqualTo(7001L);
        assertThat(query.getWarehouseId()).isEqualTo(3001L);
        assertThat(query.getStatus()).isEqualTo("POSTED");
        assertThat(query.getReturnDateFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(query.getReturnDateTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    private void assertCsvAttachment(MvcResult result, String filename) {
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''" + filename)
                .doesNotContain("filename=" + filename);
    }
}
