package com.tuowei.erp.system.auth.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证读写兼容门面：仅依赖会话与账户两个协作者，逐一委托原公开 API 并保留各自事务语义。
 * 会话签发 → {@link AuthSessionService}；账户自助（密码/资料/偏好/信息/菜单）→ {@link AuthAccountService}。
 */
@Service
public class AuthService {

    private final AuthSessionService sessionService;
    private final AuthAccountService accountService;

    public AuthService(AuthSessionService sessionService, AuthAccountService accountService) {
        this.sessionService = sessionService;
        this.accountService = accountService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        return sessionService.login(request, httpRequest);
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        return sessionService.refresh(request);
    }

    public void logout(LogoutRequest request) {
        sessionService.logout(request);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        accountService.changePassword(request);
    }

    @Transactional
    public UserInfoResponse updateProfile(UpdateProfileRequest request) {
        return accountService.updateProfile(request);
    }

    public UserInfoResponse getUserInfo() {
        return accountService.getUserInfo();
    }

    @Transactional
    public UserInfoResponse updatePreferences(UpdatePreferencesRequest request) {
        return accountService.updatePreferences(request);
    }

    public List<MenuResponse> getRuntimeMenuTree() {
        return accountService.getRuntimeMenuTree();
    }
}
