package com.tuowei.erp.system.role;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.role.service.RoleDataScopeService;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
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
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private RoleDataScopeService roleDataScopeService;

    @Test
    @WithErpUser(authorities = "system:role:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(roleService.list(any(RolePageQuery.class))).thenReturn(new PageResponse<>(
                3,
                20,
                1,
                List.of(response(3001L, "FINANCE_ADMIN"))
        ));

        mockMvc.perform(get("/api/system/roles")
                        .param("pageNo", "3")
                        .param("pageSize", "20")
                        .param("keyword", "finance")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].roleCode").value("FINANCE_ADMIN"));

        ArgumentCaptor<RolePageQuery> queryCaptor = ArgumentCaptor.forClass(RolePageQuery.class);
        verify(roleService).list(queryCaptor.capture());
        RolePageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(3);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isEqualTo("finance");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @WithErpUser(authorities = "system:role:view")
    void createRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(roleService);
    }

    @Test
    @WithErpUser(authorities = "system:role:create")
    void createRejectsMissingRoleCodeBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "财务管理员"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(roleService);
    }

    @Test
    @WithErpUser(authorities = "system:role:create")
    void createDelegatesValidatedPayloadToService() throws Exception {
        when(roleService.create(any(RoleCreateRequest.class))).thenReturn(response(3001L, "FINANCE_ADMIN"));

        mockMvc.perform(post("/api/system/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3001))
                .andExpect(jsonPath("$.data.roleCode").value("FINANCE_ADMIN"));

        ArgumentCaptor<RoleCreateRequest> requestCaptor = ArgumentCaptor.forClass(RoleCreateRequest.class);
        verify(roleService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roleCode()).isEqualTo("FINANCE_ADMIN");
        assertThat(requestCaptor.getValue().roleName()).isEqualTo("财务管理员");
        assertThat(requestCaptor.getValue().remark()).isEqualTo("controller contract");
    }

    @Test
    @WithErpUser(authorities = "system:role:update")
    void updateRejectsMissingRoleNameBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/system/roles/{id}", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remark":"missing roleName"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(roleService);
    }

    @Test
    @WithErpUser(authorities = "system:role:update")
    void updateDelegatesToService() throws Exception {
        when(roleService.update(eq(3001L), any(RoleUpdateRequest.class))).thenReturn(response(3001L, "FINANCE_ADMIN"));

        mockMvc.perform(put("/api/system/roles/{id}", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "财务管理员",
                                  "remark": "updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3001));

        verify(roleService).update(eq(3001L), any(RoleUpdateRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:role:enable")
    void enableDelegatesToService() throws Exception {
        when(roleService.enable(3001L)).thenReturn(response(3001L, "FINANCE_ADMIN"));

        mockMvc.perform(post("/api/system/roles/{id}/enable", 3001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(roleService).enable(3001L);
    }

    @Test
    @WithErpUser(authorities = "system:role:assign-menu")
    void assignMenusRejectsEmptyMenuList() throws Exception {
        mockMvc.perform(put("/api/system/roles/{id}/menus", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(roleService);
    }

    @Test
    @WithErpUser(authorities = "system:role:assign-menu")
    void assignMenusDelegatesToService() throws Exception {
        when(roleService.assignMenus(eq(3001L), any(RoleMenuAssignRequest.class)))
                .thenReturn(new RoleMenuAssignmentResponse(3001L, List.of(5001L, 5002L)));

        mockMvc.perform(put("/api/system/roles/{id}/menus", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuIds":[5001,5002]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(3001))
                .andExpect(jsonPath("$.data.menuIds[0]").value(5001))
                .andExpect(jsonPath("$.data.menuIds[1]").value(5002));

        verify(roleService).assignMenus(eq(3001L), any(RoleMenuAssignRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:role:view")
    void getDataScopeDelegatesToService() throws Exception {
        when(roleDataScopeService.getAssigned(3001L)).thenReturn(
                new RoleDataScopeResponse(3001L, false, true, false, false, List.of(4501L))
        );

        mockMvc.perform(get("/api/system/roles/{id}/data-scope", 3001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(3001))
                .andExpect(jsonPath("$.data.deptScoped").value(true))
                .andExpect(jsonPath("$.data.warehouseIds[0]").value(4501));

        verify(roleDataScopeService).getAssigned(3001L);
    }

    @Test
    @WithErpUser(authorities = "system:role:assign-data-scope")
    void assignDataScopeDelegatesToService() throws Exception {
        when(roleDataScopeService.assign(eq(3001L), any(RoleDataScopeAssignRequest.class)))
                .thenReturn(new RoleDataScopeResponse(3001L, true, false, false, false, List.of()));

        mockMvc.perform(put("/api/system/roles/{id}/data-scope", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hasAllScope": true,
                                  "deptScoped": false,
                                  "postScoped": false,
                                  "selfScoped": false,
                                  "warehouseIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAllScope").value(true));

        verify(roleDataScopeService).assign(eq(3001L), any(RoleDataScopeAssignRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:role:update")
    void assignDataScopeRequiresAssignDataScopePermission() throws Exception {
        mockMvc.perform(put("/api/system/roles/{id}/data-scope", 3001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hasAllScope": false,
                                  "deptScoped": true,
                                  "postScoped": false,
                                  "selfScoped": false,
                                  "warehouseIds": []
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleDataScopeService);
    }

    private static String validCreateBody() {
        return """
                {
                  "roleCode": "FINANCE_ADMIN",
                  "roleName": "财务管理员",
                  "remark": "controller contract"
                }
                """;
    }

    private static RoleResponse response(Long id, String roleCode) {
        return new RoleResponse(
                id,
                roleCode,
                "财务管理员",
                "ACTIVE",
                "controller contract"
        );
    }
}
