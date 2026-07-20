package com.tuowei.erp.system.config;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.service.SystemConfigService;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemConfigService systemConfigService;

    @Test
    @WithErpUser(authorities = "system:config:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(systemConfigService.list(any(SystemConfigPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(response(8001L, "ERP_IMPORT_MAX_ROWS", "ACTIVE"))
        ));

        mockMvc.perform(get("/api/system/configs")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "IMPORT")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].configCode").value("ERP_IMPORT_MAX_ROWS"));

        ArgumentCaptor<SystemConfigPageQuery> queryCaptor = ArgumentCaptor.forClass(SystemConfigPageQuery.class);
        verify(systemConfigService).list(queryCaptor.capture());
        SystemConfigPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isEqualTo("IMPORT");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @WithErpUser(authorities = "system:config:view")
    void detailDelegatesToService() throws Exception {
        when(systemConfigService.getById(8001L)).thenReturn(response(8001L, "ERP_IMPORT_MAX_ROWS", "ACTIVE"));

        mockMvc.perform(get("/api/system/configs/{id}", 8001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001))
                .andExpect(jsonPath("$.data.configCode").value("ERP_IMPORT_MAX_ROWS"));

        verify(systemConfigService).getById(8001L);
    }

    @Test
    @WithErpUser(authorities = "system:config:view")
    void createRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/system/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(systemConfigService);
    }

    @Test
    @WithErpUser(authorities = "system:config:create")
    void createRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configName": "导入最大行数",
                                  "configValue": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(systemConfigService);
    }

    @Test
    @WithErpUser(authorities = "system:config:create")
    void createDelegatesValidatedPayloadToService() throws Exception {
        when(systemConfigService.create(any(SystemConfigCreateRequest.class)))
                .thenReturn(response(8001L, "ERP_IMPORT_MAX_ROWS", "ACTIVE"));

        mockMvc.perform(post("/api/system/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001))
                .andExpect(jsonPath("$.data.configCode").value("ERP_IMPORT_MAX_ROWS"));

        ArgumentCaptor<SystemConfigCreateRequest> requestCaptor = ArgumentCaptor.forClass(SystemConfigCreateRequest.class);
        verify(systemConfigService).create(requestCaptor.capture());
        SystemConfigCreateRequest request = requestCaptor.getValue();
        assertThat(request.configCode()).isEqualTo("ERP_IMPORT_MAX_ROWS");
        assertThat(request.configName()).isEqualTo("导入最大行数");
        assertThat(request.configValue()).isEqualTo("5000");
        assertThat(request.remark()).isEqualTo("控制单次 CSV 导入规模");
    }

    @Test
    @WithErpUser(authorities = "system:config:update")
    void updateRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/system/configs/{id}", 8001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configName": "",
                                  "configValue": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(systemConfigService);
    }

    @Test
    @WithErpUser(authorities = "system:config:update")
    void updateDelegatesValidatedPayloadToService() throws Exception {
        when(systemConfigService.update(eq(8001L), any(SystemConfigUpdateRequest.class)))
                .thenReturn(response(8001L, "ERP_IMPORT_MAX_ROWS", "ACTIVE"));

        mockMvc.perform(put("/api/system/configs/{id}", 8001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configName": "导入最大行数",
                                  "configValue": "8000",
                                  "remark": "按环境调整"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001));

        ArgumentCaptor<SystemConfigUpdateRequest> requestCaptor = ArgumentCaptor.forClass(SystemConfigUpdateRequest.class);
        verify(systemConfigService).update(eq(8001L), requestCaptor.capture());
        SystemConfigUpdateRequest request = requestCaptor.getValue();
        assertThat(request.configName()).isEqualTo("导入最大行数");
        assertThat(request.configValue()).isEqualTo("8000");
        assertThat(request.remark()).isEqualTo("按环境调整");
    }

    @Test
    @WithErpUser(authorities = "system:config:enable")
    void enableDelegatesToService() throws Exception {
        when(systemConfigService.enable(8001L)).thenReturn(response(8001L, "ERP_IMPORT_MAX_ROWS", "ACTIVE"));

        mockMvc.perform(post("/api/system/configs/{id}/enable", 8001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(systemConfigService).enable(8001L);
    }

    @Test
    @WithErpUser(authorities = "system:config:disable")
    void disableDelegatesToService() throws Exception {
        when(systemConfigService.disable(8001L)).thenReturn(response(8001L, "ERP_IMPORT_MAX_ROWS", "DISABLED"));

        mockMvc.perform(post("/api/system/configs/{id}/disable", 8001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(systemConfigService).disable(8001L);
    }

    private static String validCreateBody() {
        return """
                {
                  "configCode": "ERP_IMPORT_MAX_ROWS",
                  "configName": "导入最大行数",
                  "configValue": "5000",
                  "remark": "控制单次 CSV 导入规模"
                }
                """;
    }

    private static SystemConfigResponse response(Long id, String configCode, String status) {
        return new SystemConfigResponse(
                id,
                configCode,
                "导入最大行数",
                "5000",
                status,
                "控制单次 CSV 导入规模"
        );
    }
}
