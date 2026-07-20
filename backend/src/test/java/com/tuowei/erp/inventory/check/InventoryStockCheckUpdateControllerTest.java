package com.tuowei.erp.inventory.check;

import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckLineResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryStockCheckUpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryStockCheckService stockCheckService;

    @Test
    @WithErpUser(authorities = "inventory:check:view")
    void updateRequiresInventoryCheckCreatePermission() throws Exception {
        mockMvc.perform(put("/api/inventory/checks/{id}", 8001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"id":8101,"productId":7001,"actualQty":7.0000,"unitCost":10.0000,"remark":"counted"}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(stockCheckService);
    }

    @Test
    @WithErpUser(authorities = "inventory:check:create")
    void updateBindsRequestAndReturnsUpdatedCheck() throws Exception {
        when(stockCheckService.update(org.mockito.ArgumentMatchers.eq(8001L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response());

        mockMvc.perform(put("/api/inventory/checks/{id}", 8001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"id":8101,"productId":7001,"actualQty":7.0000,"unitCost":10.0000,"remark":"counted"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001L))
                .andExpect(jsonPath("$.data.lines[0].actualQty").value(7.0000))
                .andExpect(jsonPath("$.data.lines[0].differenceQty").value(2.0000));

        ArgumentCaptor<InventoryStockCheckUpdateRequest> captor = ArgumentCaptor.forClass(InventoryStockCheckUpdateRequest.class);
        verify(stockCheckService).update(org.mockito.ArgumentMatchers.eq(8001L), captor.capture());
        assertThat(captor.getValue().items()).hasSize(1);
        assertThat(captor.getValue().items().get(0).id()).isEqualTo(8101L);
        assertThat(captor.getValue().items().get(0).productId()).isEqualTo(7001L);
        assertThat(captor.getValue().items().get(0).actualQty()).isEqualByComparingTo("7.0000");
        assertThat(captor.getValue().items().get(0).unitCost()).isEqualByComparingTo("10.0000");
        assertThat(captor.getValue().items().get(0).remark()).isEqualTo("counted");
    }

    private InventoryStockCheckResponse response() {
        return new InventoryStockCheckResponse(
                8001L,
                "CHK-001",
                6001L,
                LocalDate.of(2026, 6, 18),
                "COUNTED",
                null,
                null,
                "updated",
                List.of(new InventoryStockCheckLineResponse(
                        8101L,
                        1,
                        7001L,
                        new BigDecimal("5.0000"),
                        new BigDecimal("7.0000"),
                        new BigDecimal("2.0000"),
                        new BigDecimal("10.0000"),
                        new BigDecimal("20.00"),
                        null,
                        null,
                        null,
                        "counted"
                ))
        );
    }
}
