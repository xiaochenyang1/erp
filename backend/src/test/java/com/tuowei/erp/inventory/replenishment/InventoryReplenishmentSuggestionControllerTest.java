package com.tuowei.erp.inventory.replenishment;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionService;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryReplenishmentSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryReplenishmentSuggestionService suggestionService;

    @Test
    @WithErpUser(authorities = "inventory:alert:view")
    void listRequiresReplenishmentViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/replenishment-suggestions")
                        .param("status", "DRAFT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:view")
    void listBindsQueryAndReturnsPageResponse() throws Exception {
        when(suggestionService.list(any(InventoryReplenishmentSuggestionPageQuery.class)))
                .thenReturn(new PageResponse<>(2, 20, 1, List.of(response("DRAFT"))));

        mockMvc.perform(get("/api/inventory/replenishment-suggestions")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("suggestionNo", "RS202607")
                        .param("status", "DRAFT")
                        .param("warehouseId", "8101")
                        .param("productId", "8201")
                        .param("supplierId", "8301")
                        .param("createdTimeFrom", "2026-07-01T00:00:00")
                        .param("createdTimeTo", "2026-07-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].suggestionNo").value("RS202607060001"))
                .andExpect(jsonPath("$.data.records[0].fulfillmentStatus").value("SUGGESTED"));

        ArgumentCaptor<InventoryReplenishmentSuggestionPageQuery> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionPageQuery.class);
        verify(suggestionService).list(captor.capture());
        InventoryReplenishmentSuggestionPageQuery query = captor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getSuggestionNo()).isEqualTo("RS202607");
        assertThat(query.getStatus()).isEqualTo("DRAFT");
        assertThat(query.getWarehouseId()).isEqualTo(8101L);
        assertThat(query.getProductId()).isEqualTo(8201L);
        assertThat(query.getSupplierId()).isEqualTo(8301L);
        assertThat(query.getCreatedTimeFrom()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(query.getCreatedTimeTo()).isEqualTo(LocalDateTime.of(2026, 7, 31, 23, 59, 59));
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:view")
    void createRequiresReplenishmentCreatePermission() throws Exception {
        mockMvc.perform(post("/api/inventory/replenishment-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:create")
    void createBindsRequestAndReturnsSuggestion() throws Exception {
        when(suggestionService.create(any(InventoryReplenishmentSuggestionCreateRequest.class)))
                .thenReturn(response("DRAFT"));

        mockMvc.perform(post("/api/inventory/replenishment-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestionNo").value("RS202607060001"))
                .andExpect(jsonPath("$.data.suggestedQty").value(7.0000));

        ArgumentCaptor<InventoryReplenishmentSuggestionCreateRequest> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionCreateRequest.class);
        verify(suggestionService).create(captor.capture());
        InventoryReplenishmentSuggestionCreateRequest request = captor.getValue();
        assertThat(request.ruleId()).isEqualTo(7101L);
        assertThat(request.warehouseId()).isEqualTo(8101L);
        assertThat(request.productId()).isEqualTo(8201L);
        assertThat(request.supplierId()).isEqualTo(8301L);
        assertThat(request.suggestedQty()).isEqualByComparingTo("7.0000");
        assertThat(request.expectedArrivalDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(request.remark()).isEqualTo("低库存补货");
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:view")
    void updateRequiresReplenishmentUpdatePermission() throws Exception {
        mockMvc.perform(put("/api/inventory/replenishment-suggestions/{id}", 9001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:update")
    void updateBindsRequestAndReturnsSuggestion() throws Exception {
        when(suggestionService.update(eq(9001L), any(InventoryReplenishmentSuggestionUpdateRequest.class)))
                .thenReturn(response("DRAFT"));

        mockMvc.perform(put("/api/inventory/replenishment-suggestions/{id}", 9001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestionNo").value("RS202607060001"));

        ArgumentCaptor<InventoryReplenishmentSuggestionUpdateRequest> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionUpdateRequest.class);
        verify(suggestionService).update(eq(9001L), captor.capture());
        InventoryReplenishmentSuggestionUpdateRequest request = captor.getValue();
        assertThat(request.supplierId()).isEqualTo(8301L);
        assertThat(request.suggestedQty()).isEqualByComparingTo("8.5000");
        assertThat(request.expectedArrivalDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(request.remark()).isEqualTo("调整补货计划");
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:view")
    void cancelRequiresReplenishmentCancelPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/replenishment-suggestions/{id}/cancel", 9001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"无需补货\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:cancel")
    void cancelBindsReasonAndReturnsSuggestion() throws Exception {
        when(suggestionService.cancel(eq(9001L), any(InventoryReplenishmentSuggestionCancelRequest.class)))
                .thenReturn(response("CANCELLED"));

        mockMvc.perform(post("/api/inventory/replenishment-suggestions/{id}/cancel", 9001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"无需补货\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ArgumentCaptor<InventoryReplenishmentSuggestionCancelRequest> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionCancelRequest.class);
        verify(suggestionService).cancel(eq(9001L), captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("无需补货");
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:view")
    void convertRequiresReplenishmentConvertPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/replenishment-suggestions/{id}/convert-to-purchase-order", 9001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    @WithErpUser(authorities = "inventory:replenishment:convert")
    void convertReturnsConvertedSuggestion() throws Exception {
        when(suggestionService.convertToPurchaseOrder(9001L)).thenReturn(response("CONVERTED"));

        mockMvc.perform(post("/api/inventory/replenishment-suggestions/{id}/convert-to-purchase-order", 9001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.purchaseOrderNo").value("PO202607060001"));

        verify(suggestionService).convertToPurchaseOrder(9001L);
    }

    private String createJson() {
        return """
                {
                  "ruleId": 7101,
                  "warehouseId": 8101,
                  "productId": 8201,
                  "supplierId": 8301,
                  "suggestedQty": 7.0000,
                  "expectedArrivalDate": "2026-07-12",
                  "remark": "低库存补货"
                }
                """;
    }

    private String updateJson() {
        return """
                {
                  "supplierId": 8301,
                  "suggestedQty": 8.5000,
                  "expectedArrivalDate": "2026-07-15",
                  "remark": "调整补货计划"
                }
                """;
    }

    private InventoryReplenishmentSuggestionResponse response(String status) {
        return new InventoryReplenishmentSuggestionResponse(
                9001L,
                "RS202607060001",
                "LOW_STOCK_ALERT",
                7101L,
                8101L,
                "主仓",
                8201L,
                "MAT-001",
                "原料A",
                8301L,
                "测试供应商",
                new BigDecimal("7.0000"),
                new BigDecimal("7.0000"),
                LocalDate.of(2026, 7, 12),
                status,
                "CONVERTED".equals(status) ? "PURCHASE_CREATED" : "DRAFT".equals(status) ? "SUGGESTED" : "CANCELLED",
                "CONVERTED".equals(status) ? 9901L : null,
                "CONVERTED".equals(status) ? "PO202607060001" : null,
                "低库存补货",
                LocalDateTime.of(2026, 7, 6, 9, 30)
        );
    }
}
