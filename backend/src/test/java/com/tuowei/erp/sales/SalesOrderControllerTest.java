package com.tuowei.erp.sales;

import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalesOrderService salesOrderService;

    @Test
    @WithErpUser(authorities = "sales:order:view")
    void creditPreviewReturnsProjectedExposure() throws Exception {
        when(salesOrderService.previewCredit(any())).thenReturn(new SalesOrderCreditPreviewResponse(
                9001L,
                new BigDecimal("1000.00"),
                new BigDecimal("300.00"),
                new BigDecimal("200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("260.00"),
                new BigDecimal("760.00"),
                new BigDecimal("500.00"),
                new BigDecimal("240.00"),
                false,
                false
        ));

        mockMvc.perform(post("/api/sales/orders/credit-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 9001,
                                  "lines": [
                                    {
                                      "productId": 1001,
                                      "qty": 2,
                                      "price": 100,
                                      "taxRate": 0.3
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.customerId").value("9001"))
                .andExpect(jsonPath("$.data.currentExposure").value(500))
                .andExpect(jsonPath("$.data.projectedExposure").value(760))
                .andExpect(jsonPath("$.data.exceeded").value(false));
    }
}
