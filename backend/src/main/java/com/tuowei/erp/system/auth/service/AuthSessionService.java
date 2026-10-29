package com.tuowei.erp.system.auth.service;

import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LoginUserDataScopeResponse;
import com.tuowei.erp.system.auth.web.LoginUserResponse;
import com.tuowei.erp.system.auth.web.LogoutRequest;
import com.tuowei.erp.system.auth.web.RefreshTokenRequest;
import com.tuowei.erp.system.log.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.List;

/** 认证会话签发：登录、刷新与登出的令牌流程，不改动任何用户资料。 */
@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final SystemLogService systemLogService;
    private final Object loginRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthSessionService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            SystemLogService systemLogService,
            @Autowired(required = false) LoginRateLimiter redisRateLimiter,
            @Autowired(required = false) NoOpLoginRateLimiter noOpRateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.systemLogService = systemLogService;
        this.loginRateLimiter = redisRateLimiter != null ? redisRateLimiter : noOpRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        if (loginRateLimiter instanceof LoginRateLimiter limiter) {
            limiter.assertAllowed(request.username(), clientIp);
        } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
            limiter.assertAllowed(request.username(), clientIp);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            log.warn("Login failed username={} ip={} reason={}", request.username(), clientIp, ex.getClass().getSimpleName());
            systemLogService.recordLoginFailure(request.username(), "用户名或密码错误", httpRequest);

            if (loginRateLimiter instanceof LoginRateLimiter limiter) {
                limiter.recordFailure(request.username(), clientIp);
            } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
                limiter.recordFailure(request.username(), clientIp);
            }
            throw ex;
        }
        ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
        systemLogService.recordLoginSuccess(principal.userId(), principal.username(), "登录成功", httpRequest);

        if (loginRateLimiter instanceof LoginRateLimiter limiter) {
            limiter.recordSuccess(principal.username(), clientIp);
        } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
            limiter.recordSuccess(principal.username(), clientIp);
        }

        String accessToken = jwtTokenService.createAccessToken(principal);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(principal, httpRequest);
        LoginResponse response = buildLoginResponse(
                principal,
                accessToken,
                refreshToken.token(),
                jwtTokenService.accessTokenTtlSeconds(),
                refreshToken.expiresIn()
        );
        log.info("Login succeeded userId={} username={} ip={}", principal.userId(), principal.username(), clientIp);
        return response;
    }
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.TokenRefreshResult result = refreshTokenService.rotate(request.refreshToken());
        return buildLoginResponse(
                result.principal(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.refreshExpiresIn()
        );
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private LoginResponse buildLoginResponse(
            ErpPrincipal principal,
            String accessToken,
            String refreshToken,
            long accessExpiresIn,
            long refreshExpiresIn
    ) {
        List<String> permissions = principal.permissions().stream().sorted().toList();
        LoginUserDataScopeResponse dataScope = new LoginUserDataScopeResponse(
                principal.dataScopeSnapshot().hasAllScope(),
                principal.dataScopeSnapshot().deptScoped(),
                principal.dataScopeSnapshot().postScoped(),
                principal.dataScopeSnapshot().selfScoped(),
                principal.dataScopeSnapshot().warehouseIds().stream().sorted().toList()
        );
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessExpiresIn,
                refreshExpiresIn,
                new LoginUserResponse(principal.userId(), principal.username(), principal.realName(), dataScope),
                permissions
        );
    }
}
