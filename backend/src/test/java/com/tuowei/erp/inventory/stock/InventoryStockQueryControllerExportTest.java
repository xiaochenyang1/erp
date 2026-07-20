package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.service.InventoryStockQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryBalancePageQuery;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryStockQueryControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryStockQueryService inventoryStockQueryService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void balanceExportRequiresInventoryStockViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/balances/export")
                        .param("warehouseId", "3001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(inventoryStockQueryService);
    }

    @Test
    @WithErpUser(authorities = "inventory:stock:view")
    void balanceExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("""
                warehouseId,productId,qtyOnHand
                3001,4001,10.0000
                """.replace("\n", "\r\n"));
        when(inventoryStockQueryService.exportBalances(any(InventoryBalancePageQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/inventory/balances/export")
                        .param("pageNo", "2")
                        .param("pageSize", "50")
                        .param("warehouseId", "3001")
                        .param("productId", "4001"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("warehouseId,productId,qtyOnHand\r\n3001,4001,10.0000\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''inventory-balances.csv")
                .doesNotContain("filename=inventory-balances.csv");

        ArgumentCaptor<InventoryBalancePageQuery> queryCaptor = ArgumentCaptor.forClass(InventoryBalancePageQuery.class);
        verify(inventoryStockQueryService).exportBalances(queryCaptor.capture());
        InventoryBalancePageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(50);
        assertThat(query.getWarehouseId()).isEqualTo(3001L);
        assertThat(query.getProductId()).isEqualTo(4001L);
    }
}
