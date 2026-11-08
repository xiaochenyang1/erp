package com.tuowei.erp.production.schedule;

import com.tuowei.erp.production.schedule.service.ProductionScheduleService;
import com.tuowei.erp.production.schedule.web.ProductionScheduleRequest;
import com.tuowei.erp.production.schedule.web.ProductionScheduleResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionScheduleControllerTest {
    private static final ProductionScheduleRequest REQUEST = new ProductionScheduleRequest(
            LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 20));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductionScheduleService scheduleService;

    @Test
    @WithErpUser(authorities = "production:schedule:view")
    void previewsScheduleWithViewPermission() throws Exception {
        when(scheduleService.preview(REQUEST)).thenReturn(response(false));

        mockMvc.perform(post("/api/production/schedules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-11-09",
                                  "endDate": "2026-11-20"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-11-09"))
                .andExpect(jsonPath("$.data.endDate").value("2026-11-20"))
                .andExpect(jsonPath("$.data.committed").value(false))
                .andExpect(jsonPath("$.data.scheduledCount").value(2));

        verify(scheduleService).preview(REQUEST);
    }

    @Test
    @WithErpUser(authorities = "production:schedule:run")
    void runsScheduleWithRunPermission() throws Exception {
        when(scheduleService.run(REQUEST)).thenReturn(response(true));

        mockMvc.perform(post("/api/production/schedules/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-11-09",
                                  "endDate": "2026-11-20"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.committed").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(1));

        verify(scheduleService).run(REQUEST);
    }

    @Test
    @WithErpUser(authorities = "production:schedule:view")
    void rejectsRunWithoutRunPermission() throws Exception {
        mockMvc.perform(post("/api/production/schedules/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-11-09",
                                  "endDate": "2026-11-20"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = "production:schedule:view")
    void validatesRequiredScheduleDates() throws Exception {
        mockMvc.perform(post("/api/production/schedules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-11-09"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private ProductionScheduleResponse response(boolean committed) {
        return new ProductionScheduleResponse(
                REQUEST.startDate(), REQUEST.endDate(), committed, 1, 2, 2, 0, 0,
                LocalDateTime.of(2026, 11, 8, 15, 0), List.of(), List.of()
        );
    }
}
