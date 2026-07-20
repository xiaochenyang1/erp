package com.tuowei.erp.purchase;

import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
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
class PurchaseOrderControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseOrderService purchaseOrderService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void purchaseOrderExportRequiresPurchaseOrderViewPermission() throws Exception {
        mockMvc.perform(get("/api/purchase/orders/export")
                        .param("supplierId", "3001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(purchaseOrderService);
    }

    @Test
    @WithErpUser(authorities = "purchase:order:view")
    void purchaseOrderExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("""
                orderNo,totalAmount
                PO-2026-001,1000.00
                """.replace("\n", "\r\n"));
        when(purchaseOrderService.exportOrders(any(PurchaseOrderPageQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/purchase/orders/export")
                        .param("pageNo", "2")
                        .param("pageSize", "100")
                        .param("keyword", "PO-2026")
                        .param("supplierId", "3001")
                        .param("status", "APPROVED")
                        .param("approvalStatus", "APPROVED"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("orderNo,totalAmount\r\nPO-2026-001,1000.00\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''purchase-orders.csv")
                .doesNotContain("filename=purchase-orders.csv");

        ArgumentCaptor<PurchaseOrderPageQuery> queryCaptor = ArgumentCaptor.forClass(PurchaseOrderPageQuery.class);
        verify(purchaseOrderService).exportOrders(queryCaptor.capture());
        PurchaseOrderPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(100);
        assertThat(query.getKeyword()).isEqualTo("PO-2026");
        assertThat(query.getSupplierId()).isEqualTo(3001L);
        assertThat(query.getStatus()).isEqualTo("APPROVED");
        assertThat(query.getApprovalStatus()).isEqualTo("APPROVED");
    }
}
