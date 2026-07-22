package com.tuowei.erp.dashboard;

import com.tuowei.erp.dashboard.service.OperationsDashboardService;
import com.tuowei.erp.dashboard.web.OperationsDashboardResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardSummaryResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardTodoResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationsDashboardService operationsDashboardService;

    @Test
    @WithErpUser(authorities = "workflow:view")
    void operationsDashboardReturnsAggregatedResponseForAuthenticatedUser() throws Exception {
        when(operationsDashboardService.getOperationsDashboard()).thenReturn(new OperationsDashboardResponse(
                new OperationsDashboardSummaryResponse(
                        2,
                        1,
                        3,
                        4,
                        new BigDecimal("1250.00"),
                        5,
                        new BigDecimal("890.00"),
                        6,
                        new BigDecimal("3200.00")
                ),
                List.of(new OperationsDashboardTodoResponse(
                        "workflow-9001",
                        "WORKFLOW",
                        "采购订单 PO-001 待审批",
                        "提交于 2026-06-30T09:00",
                        "HIGH",
                        "/workflow/tasks?businessType=PURCHASE_ORDER&businessId=9001&status=PENDING",
                        LocalDateTime.of(2026, 6, 30, 9, 0)
                )),
                List.of(),
                List.of(),
                LocalDateTime.of(2026, 6, 30, 10, 0)
        ));

        mockMvc.perform(get("/api/dashboard/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.summary.pendingApprovals").value(2))
                .andExpect(jsonPath("$.data.summary.overdueApprovals").value(1))
                .andExpect(jsonPath("$.data.todos[0].route").value("/workflow/tasks?businessType=PURCHASE_ORDER&businessId=9001&status=PENDING"))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-06-30T10:00:00"));
    }
}
