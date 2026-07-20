package com.tuowei.erp.system.auth;

import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.web.ChangePasswordRequest;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LoginUserDataScopeResponse;
import com.tuowei.erp.system.auth.web.LoginUserResponse;
import com.tuowei.erp.system.auth.web.LogoutRequest;
import com.tuowei.erp.system.auth.web.RefreshTokenRequest;
import com.tuowei.erp.system.auth.web.UpdateProfileRequest;
import com.tuowei.erp.system.auth.web.UserInfoResponse;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import jakarta.servlet.http.HttpServletRequest;
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
class AuthControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    void loginDelegatesValidatedPayloadAndRequestToService() throws Exception {
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo","password":"P@ssw0rd123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.username").value("demo"))
                .andExpect(jsonPath("$.data.permissions[0]").value("report:view"));

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(requestCaptor.capture(), any(HttpServletRequest.class));
        assertThat(requestCaptor.getValue().username()).isEqualTo("demo");
        assertThat(requestCaptor.getValue().password()).isEqualTo("P@ssw0rd123");
    }

    @Test
    void refreshRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    void refreshDelegatesValidatedPayloadToService() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));

        ArgumentCaptor<RefreshTokenRequest> requestCaptor = ArgumentCaptor.forClass(RefreshTokenRequest.class);
        verify(authService).refresh(requestCaptor.capture());
        assertThat(requestCaptor.getValue().refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void logoutRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    void logoutDelegatesValidatedPayloadToService() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<LogoutRequest> requestCaptor = ArgumentCaptor.forClass(LogoutRequest.class);
        verify(authService).logout(requestCaptor.capture());
        assertThat(requestCaptor.getValue().refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @WithErpUser
    void changePasswordRejectsShortNewPasswordBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"P@ssw0rd123","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    @WithErpUser
    void changePasswordRejectsNewPasswordWithoutDigitBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"P@ssw0rd123","newPassword":"passwordonly"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    @WithErpUser
    void changePasswordRejectsNewPasswordAboveBcryptByteLimitBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"P@ssw0rd123","newPassword":"%s"}
                                """.formatted(passwordAboveBcryptByteLimit())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    @WithErpUser
    void changePasswordDelegatesValidatedPayloadToService() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"P@ssw0rd123","newPassword":"N3wP@ssw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<ChangePasswordRequest> requestCaptor = ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(authService).changePassword(requestCaptor.capture());
        assertThat(requestCaptor.getValue().oldPassword()).isEqualTo("P@ssw0rd123");
        assertThat(requestCaptor.getValue().newPassword()).isEqualTo("N3wP@ssw0rd!");
    }

    @Test
    @WithErpUser
    void updateProfileRejectsInvalidEmailBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"realName":"Demo","email":"not-an-email","mobile":"13800000000","avatar":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(authService);
    }

    @Test
    @WithErpUser
    void updateProfileDelegatesValidatedPayloadToService() throws Exception {
        when(authService.updateProfile(any(UpdateProfileRequest.class))).thenReturn(
                new UserInfoResponse(
                        1001L,
                        "demo",
                        "演示用户",
                        "demo@example.com",
                        "13800000000",
                        "https://cdn.example.com/a.png",
                        List.of("管理员"),
                        List.of("report:view")
                )
        );

        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName":"演示用户",
                                  "email":"demo@example.com",
                                  "mobile":"13800000000",
                                  "avatar":"https://cdn.example.com/a.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.email").value("demo@example.com"))
                .andExpect(jsonPath("$.data.avatar").value("https://cdn.example.com/a.png"));

        ArgumentCaptor<UpdateProfileRequest> requestCaptor = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(authService).updateProfile(requestCaptor.capture());
        assertThat(requestCaptor.getValue().realName()).isEqualTo("演示用户");
        assertThat(requestCaptor.getValue().email()).isEqualTo("demo@example.com");
        assertThat(requestCaptor.getValue().mobile()).isEqualTo("13800000000");
        assertThat(requestCaptor.getValue().avatar()).isEqualTo("https://cdn.example.com/a.png");
    }

    @Test
    @WithErpUser
    void runtimeMenuTreeDelegatesToServiceForAuthenticatedUser() throws Exception {
        when(authService.getRuntimeMenuTree()).thenReturn(List.of(
                new MenuResponse(
                        6001L,
                        0L,
                        "CATALOG",
                        "WORKFLOW",
                        "审批中心",
                        "/workflow",
                        null,
                        null,
                        1,
                        1,
                        "ACTIVE",
                        List.of(new MenuResponse(
                                6002L,
                                6001L,
                                "MENU",
                                "WORKFLOW_TASK",
                                "审批待办",
                                "/workflow/tasks",
                                "workflow/task/index",
                                "workflow:view",
                                10,
                                1,
                                "ACTIVE",
                                List.of()
                        ))
                )
        ));

        mockMvc.perform(get("/api/auth/runtime-menu-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].menuCode").value("WORKFLOW"))
                .andExpect(jsonPath("$.data[0].children[0].path").value("/workflow/tasks"));

        verify(authService).getRuntimeMenuTree();
    }

    private static LoginResponse loginResponse() {
        return new LoginResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                3600L,
                86400L,
                new LoginUserResponse(
                        1001L,
                        "demo",
                        "演示用户",
                        new LoginUserDataScopeResponse(true, false, false, false, List.of(1L))
                ),
                List.of("report:view")
        );
    }

    private static String passwordAboveBcryptByteLimit() {
        return "Password123" + "中".repeat(21);
    }
}
