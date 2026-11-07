package com.tuowei.erp.system.auth;

import com.tuowei.erp.system.auth.service.AuthAccountService;
import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.service.AuthSessionService;
import com.tuowei.erp.system.auth.web.ChangePasswordRequest;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LogoutRequest;
import com.tuowei.erp.system.auth.web.RefreshTokenRequest;
import com.tuowei.erp.system.auth.web.UpdatePreferencesRequest;
import com.tuowei.erp.system.auth.web.UpdateProfileRequest;
import com.tuowei.erp.system.auth.web.UserInfoResponse;
import com.tuowei.erp.system.menu.web.MenuResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnSessionAndAccountCollaborators() {
        assertThat(constructorDependencies(AuthService.class))
                .containsExactlyInAnyOrder(AuthSessionService.class, AuthAccountService.class);
        assertThat(constructorDependencies(AuthSessionService.class))
                .doesNotContain(AuthService.class, AuthAccountService.class);
        assertThat(constructorDependencies(AuthAccountService.class))
                .doesNotContain(AuthService.class, AuthSessionService.class);
    }
    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        AuthSessionService sessionService = mock(AuthSessionService.class);
        AuthAccountService accountService = mock(AuthAccountService.class);
        AuthService service = new AuthService(sessionService, accountService);

        LoginRequest loginRequest = mock(LoginRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        RefreshTokenRequest refreshRequest = mock(RefreshTokenRequest.class);
        LogoutRequest logoutRequest = mock(LogoutRequest.class);
        ChangePasswordRequest changePasswordRequest = mock(ChangePasswordRequest.class);
        UpdateProfileRequest updateProfileRequest = mock(UpdateProfileRequest.class);
        UpdatePreferencesRequest updatePreferencesRequest = mock(UpdatePreferencesRequest.class);
        LoginResponse loginResponse = mock(LoginResponse.class);
        LoginResponse refreshResponse = mock(LoginResponse.class);
        UserInfoResponse profileResponse = mock(UserInfoResponse.class);
        UserInfoResponse userInfoResponse = mock(UserInfoResponse.class);
        UserInfoResponse preferencesResponse = mock(UserInfoResponse.class);
        List<MenuResponse> menuTree = List.of(mock(MenuResponse.class));

        when(sessionService.login(loginRequest, httpRequest)).thenReturn(loginResponse);
        when(sessionService.refresh(refreshRequest)).thenReturn(refreshResponse);
        when(accountService.updateProfile(updateProfileRequest)).thenReturn(profileResponse);
        when(accountService.getUserInfo()).thenReturn(userInfoResponse);
        when(accountService.updatePreferences(updatePreferencesRequest)).thenReturn(preferencesResponse);
        when(accountService.getRuntimeMenuTree()).thenReturn(menuTree);

        assertThat(service.login(loginRequest, httpRequest)).isSameAs(loginResponse);
        assertThat(service.refresh(refreshRequest)).isSameAs(refreshResponse);
        service.logout(logoutRequest);
        service.changePassword(changePasswordRequest);
        assertThat(service.updateProfile(updateProfileRequest)).isSameAs(profileResponse);
        assertThat(service.getUserInfo()).isSameAs(userInfoResponse);
        assertThat(service.updatePreferences(updatePreferencesRequest)).isSameAs(preferencesResponse);
        assertThat(service.getRuntimeMenuTree()).isSameAs(menuTree);

        verify(sessionService).login(loginRequest, httpRequest);
        verify(sessionService).refresh(refreshRequest);
        verify(sessionService).logout(logoutRequest);
        verify(accountService).changePassword(changePasswordRequest);
        verify(accountService).updateProfile(updateProfileRequest);
        verify(accountService).getUserInfo();
        verify(accountService).updatePreferences(updatePreferencesRequest);
        verify(accountService).getRuntimeMenuTree();
    }
    @Test
    void facadeAndAccountServiceKeepAccountMutationsAsRequiredWriteTransaction() {
        assertRequiredWriteTransaction(AuthService.class, "changePassword", ChangePasswordRequest.class);
        assertRequiredWriteTransaction(AuthService.class, "updateProfile", UpdateProfileRequest.class);
        assertRequiredWriteTransaction(AuthService.class, "updatePreferences", UpdatePreferencesRequest.class);

        assertRequiredWriteTransaction(AuthAccountService.class, "changePassword", ChangePasswordRequest.class);
        assertRequiredWriteTransaction(AuthAccountService.class, "updateProfile", UpdateProfileRequest.class);
        assertRequiredWriteTransaction(AuthAccountService.class, "updatePreferences", UpdatePreferencesRequest.class);
    }

    @Test
    void sessionAndReadMethodsStayNonTransactional() {
        assertNoTransaction(AuthService.class, "login", LoginRequest.class, HttpServletRequest.class);
        assertNoTransaction(AuthService.class, "refresh", RefreshTokenRequest.class);
        assertNoTransaction(AuthService.class, "logout", LogoutRequest.class);
        assertNoTransaction(AuthService.class, "getUserInfo");
        assertNoTransaction(AuthService.class, "getRuntimeMenuTree");

        assertNoTransaction(AuthSessionService.class, "login", LoginRequest.class, HttpServletRequest.class);
        assertNoTransaction(AuthSessionService.class, "refresh", RefreshTokenRequest.class);
        assertNoTransaction(AuthSessionService.class, "logout", LogoutRequest.class);
        assertNoTransaction(AuthAccountService.class, "getUserInfo");
        assertNoTransaction(AuthAccountService.class, "getRuntimeMenuTree");
    }

    private static void assertRequiredWriteTransaction(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Transactional transactional = transactionalOf(type, methodName, parameterTypes);
        assertThat(transactional)
                .as("%s#%s should carry @Transactional", type.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isFalse();
    }

    private static void assertNoTransaction(Class<?> type, String methodName, Class<?>... parameterTypes) {
        assertThat(transactionalOf(type, methodName, parameterTypes))
                .as("%s#%s should not carry @Transactional", type.getSimpleName(), methodName)
                .isNull();
    }

    private static Transactional transactionalOf(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            return method.getAnnotation(Transactional.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}
