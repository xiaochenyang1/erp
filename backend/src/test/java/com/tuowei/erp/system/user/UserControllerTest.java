package com.tuowei.erp.system.user;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.user.service.UserDataScopeService;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserResponse;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserRoleAssignmentResponse;
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserDataScopeService userDataScopeService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(userService.list(any(UserPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                10,
                1,
                List.of(response(9001L, "alice"))
        ));

        mockMvc.perform(get("/api/system/users")
                        .param("pageNo", "2")
                        .param("pageSize", "10")
                        .param("keyword", "ali")
                        .param("status", "ACTIVE")
                        .param("deptId", "11")
                        .param("postId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"));

        ArgumentCaptor<UserPageQuery> queryCaptor = ArgumentCaptor.forClass(UserPageQuery.class);
        verify(userService).list(queryCaptor.capture());
        UserPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(10);
        assertThat(query.getKeyword()).isEqualTo("ali");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
        assertThat(query.getDeptId()).isEqualTo(11L);
        assertThat(query.getPostId()).isEqualTo(12L);
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void createRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:create")
    void createRejectsWeakPasswordBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob",
                                  "password": "weak",
                                  "realName": "Bob"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:create")
    void createRejectsPasswordAboveBcryptByteLimitBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob",
                                  "password": "%s",
                                  "realName": "Bob"
                                }
                                """.formatted(passwordAboveBcryptByteLimit())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:create")
    void createDelegatesValidatedPayloadToService() throws Exception {
        when(userService.create(any(UserCreateRequest.class))).thenReturn(response(9002L, "bob"));

        mockMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9002))
                .andExpect(jsonPath("$.data.username").value("bob"));

        ArgumentCaptor<UserCreateRequest> requestCaptor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().username()).isEqualTo("bob");
        assertThat(requestCaptor.getValue().password()).isEqualTo("Password12345");
        assertThat(requestCaptor.getValue().realName()).isEqualTo("Bob");
    }

    @Test
    @WithErpUser(authorities = "system:user:reset-password")
    void resetPasswordRequiresValidPasswordPayload() throws Exception {
        mockMvc.perform(post("/api/system/users/{id}/reset-password", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:reset-password")
    void resetPasswordRejectsPasswordAboveBcryptByteLimitBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/users/{id}/reset-password", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"%s"}
                                """.formatted(passwordAboveBcryptByteLimit())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:reset-password")
    void resetPasswordRejectsPasswordWithoutDigitBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/users/{id}/reset-password", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"passwordonly"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:reset-password")
    void resetPasswordDelegatesToService() throws Exception {
        when(userService.resetPassword(eq(9002L), any(ResetPasswordRequest.class))).thenReturn(response(9002L, "bob"));

        mockMvc.perform(post("/api/system/users/{id}/reset-password", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"newStrongPassword123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9002));

        verify(userService).resetPassword(eq(9002L), any(ResetPasswordRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:user:assign-role")
    void assignRolesRejectsEmptyRoleList() throws Exception {
        mockMvc.perform(put("/api/system/users/{id}/roles", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithErpUser(authorities = "system:user:assign-role")
    void assignRolesDelegatesToService() throws Exception {
        when(userService.assignRoles(eq(9002L), any(UserRoleAssignRequest.class)))
                .thenReturn(new UserRoleAssignmentResponse(9002L, List.of(3001L, 3002L)));

        mockMvc.perform(put("/api/system/users/{id}/roles", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":[3001,3002]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(9002))
                .andExpect(jsonPath("$.data.roleIds[0]").value(3001))
                .andExpect(jsonPath("$.data.roleIds[1]").value(3002));

        verify(userService).assignRoles(eq(9002L), any(UserRoleAssignRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void getDataScopeDelegatesToService() throws Exception {
        when(userDataScopeService.getAssigned(9002L)).thenReturn(
                new UserDataScopeResponse(
                        9002L, false, true, false, true, List.of(4501L),
                        false, true, true, true, List.of(4501L, 4509L)
                )
        );

        mockMvc.perform(get("/api/system/users/{id}/data-scope", 9002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(9002))
                .andExpect(jsonPath("$.data.deptScoped").value(true))
                .andExpect(jsonPath("$.data.selfScoped").value(true))
                .andExpect(jsonPath("$.data.warehouseIds[0]").value(4501))
                .andExpect(jsonPath("$.data.effectivePostScoped").value(true))
                .andExpect(jsonPath("$.data.effectiveWarehouseIds[1]").value(4509));

        verify(userDataScopeService).getAssigned(9002L);
    }

    @Test
    @WithErpUser(authorities = "system:user:assign-data-scope")
    void assignDataScopeDelegatesToService() throws Exception {
        when(userDataScopeService.assign(eq(9002L), any(UserDataScopeAssignRequest.class)))
                .thenReturn(new UserDataScopeResponse(
                        9002L, false, false, false, false, List.of(4501L, 4502L),
                        false, false, false, false, List.of(4501L, 4502L)
                ));

        mockMvc.perform(put("/api/system/users/{id}/data-scope", 9002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hasAllScope": false,
                                  "deptScoped": false,
                                  "postScoped": false,
                                  "selfScoped": false,
                                  "warehouseIds": [4501, 4502]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseIds[0]").value(4501))
                .andExpect(jsonPath("$.data.warehouseIds[1]").value(4502));

        ArgumentCaptor<UserDataScopeAssignRequest> captor = ArgumentCaptor.forClass(UserDataScopeAssignRequest.class);
        verify(userDataScopeService).assign(eq(9002L), captor.capture());
        assertThat(captor.getValue().warehouseIds()).containsExactly(4501L, 4502L);
    }

    @Test
    @WithErpUser(authorities = "system:user:update")
    void assignDataScopeRequiresAssignDataScopePermission() throws Exception {
        mockMvc.perform(put("/api/system/users/{id}/data-scope", 9002L)
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
                .andExpect(status().isForbidden());

        verifyNoInteractions(userDataScopeService);
    }

    private static String validCreateBody() {
        return """
                {
                  "username": "bob",
                  "password": "Password12345",
                  "employeeNo": "EMP-BOB",
                  "realName": "Bob",
                  "mobile": "13800000000",
                  "deptId": 11,
                  "postId": 12,
                  "remark": "controller contract"
                }
                """;
    }

    private static UserResponse response(Long id, String username) {
        return new UserResponse(
                id,
                username,
                "EMP-" + id,
                username,
                null,
                "13800000000",
                null,
                11L,
                12L,
                "ACTIVE",
                "controller contract"
        );
    }

    private static String passwordAboveBcryptByteLimit() {
        return "Password123" + "中".repeat(21);
    }
}
